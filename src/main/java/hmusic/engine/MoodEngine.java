package hmusic.engine;

import hmusic.model.*;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.LockSupport;

/**
 * Le compositeur génératif.
 * <p>
 * Aucune musique n'est stockée : un thread dédié compose en temps réel, double-croche par
 * double-croche, en lisant l'humeur courante. On peut donc faire « glisser » l'humeur
 * pendant la lecture et la musique se transforme en continu.
 * <ul>
 *   <li>tempo, densité, nuances, swing, réverbération : changent à chaque pas</li>
 *   <li>mode, accords, instruments : changent au début de chaque mesure (pour rester harmonieux)</li>
 *   <li>un « morceau » dure {@value #BARS_PER_TRACK} mesures, puis un nouveau est composé</li>
 * </ul>
 */
public class MoodEngine {

    public static final int CH_MELODY = 0, CH_PAD = 1, CH_BASS = 2, CH_ARP = 3, CH_DRUMS = 9;
    public static final int BARS_PER_TRACK = 32;

    private static final int[] USED_CHANNELS = {CH_MELODY, CH_PAD, CH_BASS, CH_ARP, CH_DRUMS};
    private static final int REST = Integer.MIN_VALUE;
    private static final int[] KEYS = {0, 2, 3, 5, 7, 8, 9, 10}; // Do, Ré, Mib, Fa, Sol, Lab, La, Sib
    private static final long DRUM_NANOS = 150_000_000L;

    private record PendingOff(long time, int channel, int pitch) {}

    private final Synthesizer synth;
    private final MidiChannel[] channels;
    private final String soundbankName;
    private final MidiRecorder recorder = new MidiRecorder();
    private final List<EngineListener> listeners = new CopyOnWriteArrayList<>();
    private final PriorityQueue<PendingOff> offs = new PriorityQueue<>(Comparator.comparingLong(PendingOff::time));
    private final Object lock = new Object();
    private final Random rnd = new Random();
    private final Thread thread;

    // --- état partagé avec l'interface (volatile) ---
    private volatile Mood mood = Mood.NEUTRAL;
    private volatile boolean playing;
    private volatile boolean running = true;
    private volatile boolean skipRequested;
    private volatile int volume = 80;
    private volatile MusicalProfile profile = MusicalProfile.from(Mood.NEUTRAL);
    private volatile String title = "";

    // --- état musical, utilisé uniquement par le thread audio ---
    private Scale scale;
    private Instrumentation instruments;
    private Sector trackSector;
    private int key;
    private int[] progression;
    private int[] motif;      // mélodie de 2 mesures (32 pas), en degrés de la gamme
    private int chordDegree;
    private int step, bar;
    private long stepNanos;
    private long trackNanos;

    public MoodEngine() throws MidiUnavailableException {
        synth = MidiSystem.getSynthesizer();
        synth.open();
        soundbankName = loadCustomSoundbank();
        channels = synth.getChannels();
        newTrack(true);
        thread = new Thread(this::loop, "hmusic-compositeur");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    // =====================================================================
    //  API publique (appelée depuis l'interface)
    // =====================================================================

    public void addListener(EngineListener l) { listeners.add(l); }

    public void setMood(Mood m) { mood = m; }

    public Mood getMood() { return mood; }

    public MusicalProfile getProfile() { return profile; }

    public String getTitle() { return title; }

    public String getSoundbankName() { return soundbankName; }

    public boolean isPlaying() { return playing; }

    public boolean hasRecording() { return recorder.hasNotes(); }

    /** Passe au morceau suivant (nouvelle mélodie, nouvelle tonalité, nouveau titre). */
    public void requestNewTrack() {
        skipRequested = true;
        LockSupport.unpark(thread);
    }

    public void play() {
        if (playing) return;
        recorder.resume();
        synchronized (lock) {
            playing = true;
            lock.notifyAll();
        }
        for (EngineListener l : listeners) l.onPlayStateChanged(true);
    }

    public void pause() {
        if (!playing) return;
        playing = false;
        recorder.pause();
        LockSupport.unpark(thread);
        for (EngineListener l : listeners) l.onPlayStateChanged(false);
    }

    public void toggle() {
        if (playing) pause(); else play();
    }

    /** Volume général de 0 à 100. */
    public void setVolume(int v) {
        volume = Math.max(0, Math.min(100, v));
        applyVolume();
    }

    public void exportMidi(File file) throws IOException {
        recorder.write(file);
    }

    public void shutdown() {
        running = false;
        playing = false;
        synchronized (lock) {
            lock.notifyAll();
        }
        LockSupport.unpark(thread);
        try {
            thread.join(600);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        synth.close();
    }

    // =====================================================================
    //  Boucle temps réel
    // =====================================================================

    private void loop() {
        long next = System.nanoTime();
        while (running) {
            if (!playing) {
                silence();
                synchronized (lock) {
                    while (!playing && running) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
                step = 0; // on reprend proprement en début de mesure
                next = System.nanoTime();
                continue;
            }
            long now = System.nanoTime();
            flushOffs(now);
            if (now >= next) {
                try {
                    next += tick();
                } catch (RuntimeException ex) {
                    ex.printStackTrace();
                    next = now + 100_000_000L;
                }
                if (System.nanoTime() - next > 250_000_000L) next = System.nanoTime(); // on a pris du retard
            }
            long wake = next;
            PendingOff p = offs.peek();
            if (p != null && p.time() < wake) wake = p.time();
            long wait = wake - System.nanoTime();
            if (wait > 0) LockSupport.parkNanos(wait);
        }
        silence();
    }

    /** Joue un pas (une double-croche) et renvoie sa durée en nanosecondes. */
    private long tick() {
        if (skipRequested) {
            skipRequested = false;
            releaseAll();
            newTrack(true);
        }
        if (step == 0) startBar();

        MusicalProfile p = MusicalProfile.from(mood); // paramètres continus, relus à chaque pas
        profile = p;
        stepNanos = 60_000_000_000L / p.bpm() / 4;
        int vel = p.baseVelocity();

        playDrums(p, vel);
        playBass(p, vel);
        playArp(p, vel);
        playMelody(p, vel);

        final int s = step, b = bar;
        final long t = trackNanos;
        for (EngineListener l : listeners) l.onStep(s, b, t);

        double sw = p.swing();
        long dur = (step % 2 == 0) ? Math.round(stepNanos * (1 + sw)) : Math.round(stepNanos * (1 - sw));
        trackNanos += dur;
        if (++step == 16) {
            step = 0;
            bar++;
        }
        return dur;
    }

    private void startBar() {
        Mood m = mood;
        MusicalProfile p = MusicalProfile.from(m);

        if (bar >= BARS_PER_TRACK) {
            newTrack(true);                       // fin du morceau : on enchaîne sur le suivant
        } else if (m.sector() != trackSector && bar >= 2) {
            newTrack(false);                      // l'humeur a changé de famille : nouveau morceau, même tonalité
        }

        if (p.instruments() != instruments) applyInstruments(p.instruments());
        if (p.scale() != scale) {
            scale = p.scale();
            progression = pickProgression();
        } else if (bar > 0 && bar % 8 == 0) {
            progression = pickProgression();      // nouvelle « section »
            generateMotif(p);
        } else if (bar % 4 == 2) {
            mutateMotif(0.2);                     // petite variation pour ne pas lasser
        }
        chordDegree = progression[bar % progression.length];

        int reverb = p.reverb();
        for (int c : new int[]{CH_MELODY, CH_PAD, CH_BASS, CH_ARP}) control(c, 91, reverb);
        control(CH_DRUMS, 91, reverb / 2);

        long barNanos = 16 * (60_000_000_000L / p.bpm() / 4);
        playPad(p, barNanos);
        if (bar % 8 == 0 && p.drums() > 0.5) noteOn(CH_DRUMS, 49, p.baseVelocity(), 1_000_000_000L); // cymbale
    }

    private void newTrack(boolean changeKey) {
        Mood m = mood;
        MusicalProfile p = MusicalProfile.from(m);
        trackSector = m.sector();
        if (changeKey || scale == null) key = KEYS[rnd.nextInt(KEYS.length)];
        scale = p.scale();
        progression = pickProgression();
        if (p.instruments() != instruments) applyInstruments(p.instruments());
        bar = 0;
        step = 0;
        trackNanos = 0;
        generateMotif(p);
        title = TitleGenerator.generate(trackSector, rnd);
        profile = p;
        final String t = title;
        for (EngineListener l : listeners) l.onTrackChanged(t, p);
    }

    // =====================================================================
    //  Composition
    // =====================================================================

    private int[] pickProgression() {
        int[][] all = scale.progressions();
        return all[rnd.nextInt(all.length)];
    }

    /** Tonique entre Fa#2 et Fa3 selon la tonalité. */
    private int tonic() {
        int t = 48 + key;
        return t > 53 ? t - 12 : t;
    }

    /**
     * Compose un motif mélodique de 2 mesures : marche aléatoire sur les degrés de la gamme,
     * plus dense quand l'énergie est haute, avec une 2e mesure qui « répond » à la 1re.
     */
    private void generateMotif(MusicalProfile p) {
        motif = new int[32];
        Arrays.fill(motif, REST);
        double d = p.density();
        int deg = 2 + rnd.nextInt(5);
        for (int i = 0; i < 32; i++) {
            double prob;
            if (i % 4 == 0) prob = 0.30 + d * 0.55;      // temps forts
            else if (i % 2 == 0) prob = d * 0.50;         // croches
            else prob = d * d * 0.30;                     // double-croches
            if (i == 0) prob = 0.95;
            if (i >= 28) prob *= 0.3;                     // respiration en fin de phrase
            if (rnd.nextDouble() < prob) {
                int move = rnd.nextInt(5) - 2;
                if (rnd.nextDouble() < 0.12) move *= 2;   // petit saut de temps en temps
                deg = Math.max(0, Math.min(11, deg + move));
                motif[i] = deg;
            }
        }
        // question / réponse : la 2e mesure reprend partiellement la 1re
        for (int i = 16; i < 28; i++) {
            if (motif[i - 16] != REST && rnd.nextDouble() < 0.5) {
                motif[i] = Math.max(0, Math.min(11, motif[i - 16] + rnd.nextInt(3) - 1));
            }
        }
        motif[24] = rnd.nextBoolean() ? 0 : 7; // on retombe sur la tonique
    }

    private void mutateMotif(double amount) {
        for (int i = 0; i < motif.length; i++) {
            if (motif[i] != REST && rnd.nextDouble() < amount) {
                motif[i] = Math.max(0, Math.min(11, motif[i] + rnd.nextInt(3) - 1));
            }
        }
    }

    /** Sur les temps forts, on recale la mélodie sur une note de l'accord. */
    private int snapToChord(int deg) {
        for (int off : new int[]{0, -1, 1, -2, 2}) {
            int d = deg + off;
            int rel = Math.floorMod(d - chordDegree, 7);
            if (rel == 0 || rel == 2 || rel == 4) return d;
        }
        return deg;
    }

    private int chordRoot() {
        return chordDegree >= 4 ? chordDegree - 7 : chordDegree; // voicing resserré
    }

    private void playPad(MusicalProfile p, long barNanos) {
        int base = tonic() + 12;
        int r = chordRoot();
        int vel = (int) (p.baseVelocity() * 0.72);
        int[] tones = p.density() < 0.5 ? new int[]{0, 2, 4, 6} : new int[]{0, 2, 4}; // septième quand c'est calme
        for (int t : tones) noteOn(CH_PAD, scale.pitch(base, r + t), vel, (long) (barNanos * 0.98));
    }

    private void playMelody(MusicalProfile p, int vel) {
        if (bar == 0) return;                          // une mesure d'intro sans mélodie
        if (bar % 8 == 7 && step >= 8) return;         // respiration en fin de section
        int idx = (bar % 2) * 16 + step;
        int deg = motif[idx];
        if (deg == REST) return;
        if (step % 8 == 0) deg = snapToChord(deg);
        int len = 1;
        while (len < 8 && motif[(idx + len) % 32] == REST) len++;
        double legato = 0.95 - p.density() * 0.35;
        int pitch = scale.pitch(tonic() + 12, deg);
        int v = vel + (step % 4 == 0 ? 8 : 0) + rnd.nextInt(9) - 4;
        noteOn(CH_MELODY, pitch, v, (long) (len * stepNanos * legato));
    }

    private void playBass(MusicalProfile p, int vel) {
        int root = scale.pitch(tonic() - 12, chordDegree);
        if (root > 45) root -= 12;
        int fifth = scale.pitch(tonic() - 12, chordDegree + 4);
        if (fifth > 50) fifth -= 12;
        double d = p.density();
        if (d < 0.35) {                               // calme : une ronde
            if (step == 0) noteOn(CH_BASS, root, vel, stepNanos * 15);
        } else if (d < 0.7) {                         // modéré : blanches + note de passage
            if (step == 0) noteOn(CH_BASS, root, vel + 5, stepNanos * 6);
            else if (step == 8) noteOn(CH_BASS, rnd.nextBoolean() ? root : fifth, vel, stepNanos * 5);
            else if (step == 14 && rnd.nextDouble() < 0.5) noteOn(CH_BASS, fifth, vel - 10, stepNanos * 2);
        } else if (step % 2 == 0) {                   // énergique : croches qui poussent
            boolean octave = step % 4 == 2 && mood.valence() > -0.3;
            noteOn(CH_BASS, octave ? root + 12 : root, vel + (step % 4 == 0 ? 8 : 0), (long) (stepNanos * 1.6));
        }
    }

    private void playArp(MusicalProfile p, int vel) {
        double d = p.density();
        if (d < 0.2) return;
        int rate = d > 0.72 ? 1 : d > 0.45 ? 2 : 4;
        if (step % rate != 0) return;
        int[] pattern = {0, 2, 4, 7, 9, 7, 4, 2};
        int i = ((bar * 16 + step) / rate) % pattern.length;
        int base = tonic() + (mood.valence() > 0.3 ? 24 : 12);
        int pitch = scale.pitch(base, chordRoot() + pattern[i]);
        noteOn(CH_ARP, pitch, (int) (vel * 0.6), (long) (stepNanos * rate * 0.9));
    }

    private void playDrums(MusicalProfile p, int vel) {
        double d = p.drums();
        if (d <= 0) return;
        if (d < 0.3) {                                // percussions légères
            if (step % 2 == 0) drum(70, (int) (vel * (step % 4 == 0 ? 0.55 : 0.4)));
            if (step == 0) drum(36, (int) (vel * 0.7));
            if (step == 12 && rnd.nextDouble() < 0.5) drum(37, (int) (vel * 0.5));
            return;
        }
        boolean dark = mood.valence() < -0.3;
        boolean fourOnFloor = d > 0.68;
        boolean kick = fourOnFloor
                ? step % 4 == 0
                : step == 0 || step == 8 || (step == 10 && rnd.nextDouble() < 0.35) || (step == 7 && rnd.nextDouble() < 0.12);
        if (kick) drum(36, vel + 12);
        if (step == 4 || step == 12) drum(dark ? 40 : (fourOnFloor ? 39 : 38), vel + 4);
        boolean openHat = fourOnFloor && step % 4 == 2;
        if (openHat) drum(46, (int) (vel * 0.55));
        else if (d > 0.5 || step % 2 == 0) drum(42, (int) (vel * (step % 4 == 2 ? 0.7 : 0.5)));
        if (bar % 4 == 3 && step >= 12 && rnd.nextDouble() < d * 0.5) drum(dark ? 41 : 48, vel); // roulement
    }

    // =====================================================================
    //  Bas niveau MIDI
    // =====================================================================

    private void drum(int note, int vel) {
        noteOn(CH_DRUMS, note, vel, DRUM_NANOS);
    }

    private void noteOn(int c, int pitch, int vel, long durNanos) {
        if (pitch < 0 || pitch > 127) return;
        int v = Math.max(1, Math.min(127, vel));
        // si la même note sonne déjà, on la relâche avant de la rejouer
        offs.removeIf(o -> {
            if (o.channel() == c && o.pitch() == pitch) {
                noteOff(c, pitch);
                return true;
            }
            return false;
        });
        channels[c].noteOn(pitch, v);
        record(ShortMessage.NOTE_ON, c, pitch, v);
        offs.add(new PendingOff(System.nanoTime() + durNanos, c, pitch));
        for (EngineListener l : listeners) l.onNote(c, pitch, v);
    }

    private void noteOff(int c, int pitch) {
        channels[c].noteOff(pitch);
        record(ShortMessage.NOTE_OFF, c, pitch, 0);
    }

    private void flushOffs(long now) {
        while (!offs.isEmpty() && offs.peek().time() <= now) {
            PendingOff p = offs.poll();
            noteOff(p.channel(), p.pitch());
        }
    }

    private void releaseAll() {
        while (!offs.isEmpty()) {
            PendingOff p = offs.poll();
            noteOff(p.channel(), p.pitch());
        }
    }

    private void silence() {
        releaseAll();
        for (int c : USED_CHANNELS) channels[c].allNotesOff();
    }

    private void control(int c, int cc, int value) {
        channels[c].controlChange(cc, value);
        record(ShortMessage.CONTROL_CHANGE, c, cc, value);
    }

    private void applyInstruments(Instrumentation in) {
        instruments = in;
        program(CH_MELODY, in.melody());
        program(CH_PAD, in.pad());
        program(CH_BASS, in.bass());
        program(CH_ARP, in.arp());
        control(CH_PAD, 93, 50); // un peu de chorus sur la nappe
        applyVolume();
    }

    private void program(int c, int prog) {
        channels[c].programChange(prog);
        record(ShortMessage.PROGRAM_CHANGE, c, prog, 0);
    }

    private void applyVolume() {
        int[] mix = new int[16];
        mix[CH_MELODY] = 105;
        mix[CH_PAD] = 72;
        mix[CH_BASS] = 100;
        mix[CH_ARP] = 70;
        mix[CH_DRUMS] = 100;
        for (int c : USED_CHANNELS) control(c, 7, mix[c] * volume / 100);
    }

    private void record(int command, int channel, int d1, int d2) {
        try {
            recorder.record(new ShortMessage(command, channel, d1, d2));
        } catch (InvalidMidiDataException ignored) {
            // message invalide : on ne l'enregistre simplement pas
        }
    }

    /** Si un fichier .sf2 est déposé dans le dossier « soundfonts », on l'utilise (meilleur son). */
    private String loadCustomSoundbank() {
        for (Path dir : soundfontDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.{sf2,SF2,dls,DLS}")) {
                for (Path p : files) {
                    try {
                        Soundbank sb = MidiSystem.getSoundbank(p.toFile());
                        if (synth.isSoundbankSupported(sb) && synth.loadAllInstruments(sb)) {
                            return p.getFileName().toString();
                        }
                    } catch (InvalidMidiDataException | IOException e) {
                        System.err.println("SoundFont illisible : " + p + " (" + e.getMessage() + ")");
                    }
                }
            } catch (IOException ignored) {
                // dossier illisible : on essaie le suivant
            }
        }
        return "banque de sons Java intégrée";
    }

    /** Dossiers « soundfonts » possibles : dossier courant, à côté du .jar, ou au-dessus (target/). */
    private static List<Path> soundfontDirs() {
        List<Path> dirs = new ArrayList<>();
        dirs.add(Path.of("soundfonts"));
        try {
            Path jar = Path.of(MoodEngine.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path base = Files.isDirectory(jar) ? jar : jar.getParent();
            if (base != null) {
                dirs.add(base.resolve("soundfonts"));
                if (base.getParent() != null) dirs.add(base.getParent().resolve("soundfonts"));
            }
        } catch (Exception ignored) {
            // emplacement du programme inconnu : on se contente du dossier courant
        }
        return dirs;
    }
}
