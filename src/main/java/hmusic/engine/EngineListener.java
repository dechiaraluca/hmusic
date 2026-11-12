package hmusic.engine;

import hmusic.model.MusicalProfile;

/**
 * Observateur du moteur (patron Observer).
 * ATTENTION : ces méthodes sont appelées depuis le thread audio, pas depuis le thread Swing.
 */
public interface EngineListener {

    /** Une note vient d'être jouée. */
    default void onNote(int channel, int pitch, int velocity) {}

    /** Un nouveau pas (double-croche) : step de 0 à 15, bar = numéro de mesure dans le morceau. */
    default void onStep(int step, int bar, long trackNanos) {}

    /** Un nouveau morceau commence. */
    default void onTrackChanged(String title, MusicalProfile profile) {}

    default void onPlayStateChanged(boolean playing) {}
}
