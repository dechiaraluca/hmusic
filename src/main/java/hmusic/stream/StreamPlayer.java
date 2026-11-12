package hmusic.stream;

import hmusic.catalogue.Morceau;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Lecteur MP3 en streaming : télécharge le flux HTTP au fur et à mesure, le décode
 * trame par trame avec JLayer et l'envoie à la carte son.
 * <p>
 * Écrire notre propre boucle de décodage (plutôt qu'utiliser le lecteur tout fait de JLayer)
 * permet la pause, l'avance dans le morceau, le volume et l'analyse spectrale en direct.
 */
public class StreamPlayer {

    /** Évènements du lecteur. ATTENTION : appelés depuis le thread de lecture. */
    public interface Listener {
        void onStarted(Morceau track);

        void onProgress(Morceau track, double seconds);

        void onSpectrum(float[] bands);

        void onFinished(Morceau track);

        void onError(Morceau track, String message);
    }

    public static final int BANDS = 48;

    private final HttpClient http;
    private final Listener listener;
    private volatile Session current;
    private volatile float volume = 0.8f;

    public StreamPlayer(HttpClient http, Listener listener) {
        this.http = http;
        this.listener = listener;
    }

    public synchronized void play(Morceau track) {
        stop();
        Session s = new Session(track);
        current = s;
        Thread t = new Thread(s, "hmusic-lecteur");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        Session s = current;
        current = null;
        if (s != null) s.stop();
    }

    public void pause() {
        Session s = current;
        if (s != null) s.setPaused(true);
    }

    public void resume() {
        Session s = current;
        if (s != null) s.setPaused(false);
    }

    public void seek(double seconds) {
        Session s = current;
        if (s != null) s.seek(seconds);
    }

    public boolean isPaused() {
        Session s = current;
        return s == null || s.paused;
    }

    /** Volume de 0 à 1. */
    public void setVolume(float v) {
        volume = Math.max(0, Math.min(1, v));
    }

    // =====================================================================

    private final class Session implements Runnable {
        private final Morceau track;
        private volatile boolean stopped;
        private volatile boolean paused;
        private volatile double seekTo = -1;
        private volatile InputStream in;
        private volatile SourceDataLine line;

        Session(Morceau track) {
            this.track = track;
        }

        void stop() {
            stopped = true;
            synchronized (this) {
                notifyAll();
            }
            closeQuietly(in);              // débloque une lecture réseau en cours
            SourceDataLine l = line;
            if (l != null) {
                l.stop();
                l.flush();
            }
        }

        void setPaused(boolean p) {
            paused = p;
            SourceDataLine l = line;
            if (l != null) {
                if (p) l.stop(); else l.start();
            }
            synchronized (this) {
                notifyAll();
            }
        }

        void seek(double seconds) {
            seekTo = Math.max(0, seconds);
            synchronized (this) {
                notifyAll();
            }
        }

        @Override
        public void run() {
            Bitstream bs = null;
            boolean finished = false;
            try {
                bs = open();
                Decoder decoder = new Decoder();
                double position = 0;       // secondes décodées
                long lastProgress = 0, lastSpectrum = 0;
                float[] window = new float[Spectrum.SIZE];
                int windowPos = 0;
                byte[] bytes = new byte[0];
                int sampleRate = 44100, channels = 2;
                boolean announced = false;

                while (!stopped) {
                    double target = seekTo;
                    if (target >= 0) {
                        seekTo = -1;
                        if (target < position) {   // retour en arrière : on rouvre le flux
                            closeQuietly(bs);
                            bs = open();
                            decoder = new Decoder();
                            position = 0;
                        }
                        while (position < target && !stopped) {   // on saute des trames sans les décoder
                            Header h = bs.readFrame();
                            if (h == null) break;
                            position += h.ms_per_frame() / 1000.0;
                            bs.closeFrame();
                        }
                        if (line != null) line.flush();
                        listener.onProgress(track, position);
                        continue;
                    }
                    if (paused) {
                        synchronized (this) {
                            while (paused && !stopped && seekTo < 0) wait(500);
                        }
                        continue;
                    }

                    Header h = bs.readFrame();
                    if (h == null) {
                        finished = true;
                        break;
                    }
                    SampleBuffer out = (SampleBuffer) decoder.decodeFrame(h, bs);
                    bs.closeFrame();

                    if (line == null) {
                        sampleRate = decoder.getOutputFrequency();
                        channels = decoder.getOutputChannels();
                        line = openLine(sampleRate, channels);
                        if (paused) line.stop();
                    }
                    if (!announced) {
                        announced = true;
                        listener.onStarted(track);
                    }

                    short[] samples = out.getBuffer();
                    int len = out.getBufferLength();
                    if (bytes.length < len * 2) bytes = new byte[len * 2];
                    float gain = volume * volume; // courbe de volume perceptuelle
                    for (int i = 0; i < len; i++) {
                        int v = Math.round(samples[i] * gain);
                        if (v > Short.MAX_VALUE) v = Short.MAX_VALUE;
                        if (v < Short.MIN_VALUE) v = Short.MIN_VALUE;
                        bytes[2 * i] = (byte) v;
                        bytes[2 * i + 1] = (byte) (v >> 8);
                    }
                    // analyse spectrale sur le signal mono (avant le volume)
                    for (int i = 0; i + channels - 1 < len; i += channels) {
                        float mono = 0;
                        for (int c = 0; c < channels; c++) mono += samples[i + c];
                        window[windowPos++] = mono / channels / 32768f;
                        if (windowPos == Spectrum.SIZE) {
                            windowPos = 0;
                            long now = System.nanoTime();
                            if (now - lastSpectrum > 30_000_000L) {
                                lastSpectrum = now;
                                listener.onSpectrum(Spectrum.bands(window, sampleRate, BANDS));
                            }
                        }
                    }
                    line.write(bytes, 0, len * 2);
                    position += h.ms_per_frame() / 1000.0;

                    long now = System.nanoTime();
                    if (now - lastProgress > 250_000_000L) {
                        lastProgress = now;
                        double buffered = (line.getBufferSize() - line.available()) / (double) (sampleRate * channels * 2);
                        listener.onProgress(track, Math.max(0, position - buffered));
                    }
                }
                if (finished && !stopped && line != null) line.drain();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!stopped) listener.onError(track, describe(e));
                finished = false;
            } finally {
                closeQuietly(bs);
                closeQuietly(in);
                SourceDataLine l = line;
                if (l != null) l.close();
            }
            if (finished && !stopped) listener.onFinished(track);
        }

        private Bitstream open() throws IOException, InterruptedException {
            HttpRequest req = HttpRequest.newBuilder(URI.create(track.urlAudio()))
                    .timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 400) {
                closeQuietly(resp.body());
                throw new IOException("flux audio indisponible (HTTP " + resp.statusCode() + ")");
            }
            in = resp.body();
            if (stopped) closeQuietly(in);
            return new Bitstream(new BufferedInputStream(in, 64 * 1024));
        }
    }

    private static SourceDataLine openLine(int sampleRate, int channels) throws LineUnavailableException {
        AudioFormat fmt = new AudioFormat(sampleRate, 16, channels, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
        int quarterSecond = sampleRate * channels * 2 / 4;
        line.open(fmt, quarterSecond);
        line.start();
        return line;
    }

    private static String describe(Exception e) {
        if (e instanceof LineUnavailableException) return "carte son indisponible";
        if (e instanceof JavaLayerException) return "flux MP3 illisible";
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception ignored) {
            // rien à faire
        }
    }

    private static void closeQuietly(Bitstream bs) {
        if (bs == null) return;
        try {
            bs.close();
        } catch (Exception ignored) {
            // rien à faire
        }
    }
}
