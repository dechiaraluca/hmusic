package hmusic.engine;

import javax.sound.midi.*;
import java.io.File;
import java.io.IOException;

/**
 * Enregistre en direct tout ce que compose le moteur, pour pouvoir l'exporter en fichier .mid
 * (ouvrable ensuite dans GarageBand, FL Studio, MuseScore…).
 */
public class MidiRecorder {

    private static final int MAX_EVENTS = 400_000;

    private final Sequence sequence;
    private final Track track;
    private long accumulatedNanos;
    private long runningSince = -1; // -1 = enregistrement en pause
    private int noteCount;

    public MidiRecorder() {
        try {
            // 500 ticks par noire à 120 BPM => 1 tick = 1 ms : idéal pour un enregistrement « temps réel »
            sequence = new Sequence(Sequence.PPQ, 500);
            track = sequence.createTrack();
            MetaMessage tempo = new MetaMessage(0x51, new byte[]{0x07, (byte) 0xA1, 0x20}, 3); // 500 000 µs / noire
            track.add(new MidiEvent(tempo, 0));
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized void resume() {
        if (runningSince < 0) runningSince = System.nanoTime();
    }

    public synchronized void pause() {
        if (runningSince >= 0) {
            accumulatedNanos += System.nanoTime() - runningSince;
            runningSince = -1;
        }
    }

    private long nowMillis() {
        long n = accumulatedNanos + (runningSince >= 0 ? System.nanoTime() - runningSince : 0);
        return n / 1_000_000L;
    }

    public synchronized void record(ShortMessage msg) {
        if (track.size() >= MAX_EVENTS) return;
        track.add(new MidiEvent(msg, nowMillis()));
        if (msg.getCommand() == ShortMessage.NOTE_ON && msg.getData2() > 0) noteCount++;
    }

    public synchronized boolean hasNotes() {
        return noteCount > 0;
    }

    public synchronized void write(File file) throws IOException {
        int[] types = MidiSystem.getMidiFileTypes(sequence);
        if (types.length == 0) throw new IOException("Aucun format de fichier MIDI disponible");
        int type = types[0];
        for (int t : types) if (t == 1) type = 1;
        MidiSystem.write(sequence, type, file);
    }
}
