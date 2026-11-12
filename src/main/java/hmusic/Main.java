package hmusic;

import hmusic.config.Settings;
import hmusic.data.MoodJournal;
import hmusic.stream.ListenController;
import hmusic.engine.MoodEngine;
import hmusic.ui.MainWindow;
import hmusic.ui.Theme;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.*;

/** Point d'entrée de hmusic. */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            MoodEngine engine;
            try {
                engine = new MoodEngine();
            } catch (MidiUnavailableException e) {
                JOptionPane.showMessageDialog(null,
                        "Impossible d'ouvrir le synthétiseur MIDI de Java :\n" + e.getMessage(),
                        "hmusic", JOptionPane.ERROR_MESSAGE);
                return;
            }
            new MainWindow(engine, new ListenController(new Settings()), new MoodJournal()).setVisible(true);
        });
    }
}
