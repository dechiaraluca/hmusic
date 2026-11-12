package hmusic.ui;

import hmusic.model.Mood;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.function.DoubleConsumer;

/** Barre de lecture en bas de la fenêtre. */
public class PlayerBar extends JPanel {

    private final CoverArt cover = new CoverArt();
    private final JLabel title = Theme.label("—", Font.BOLD, 14, Theme.TEXT);
    private final JLabel subtitle = Theme.label(" ", Font.PLAIN, 12, Theme.MUTED);
    private final IconButton playButton;
    private final Slider progress;
    private final JLabel elapsed = Theme.label("0:00", Font.PLAIN, 11, Theme.MUTED);
    private final JLabel barLabel = Theme.label("mesure 1/32", Font.PLAIN, 11, Theme.MUTED);
    private final PillButton exportButton;

    public PlayerBar(Runnable onToggle, Runnable onNext, Runnable onExport, DoubleConsumer onVolume, DoubleConsumer onSeek) {
        super(new BorderLayout(16, 0));
        progress = new Slider(0, onSeek);
        progress.setInteractive(false);
        setBackground(Theme.PLAYER);
        setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, Theme.BORDER), new EmptyBorder(12, 16, 12, 20)));

        // gauche : pochette + titre
        JPanel left = new JPanel(new BorderLayout(12, 0));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(330, 58));
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.add(Box.createVerticalGlue());
        texts.add(title);
        texts.add(Box.createVerticalStrut(3));
        texts.add(subtitle);
        texts.add(Box.createVerticalGlue());
        left.add(cover, BorderLayout.WEST);
        left.add(texts, BorderLayout.CENTER);

        // centre : boutons + progression
        playButton = new IconButton(IconButton.Glyph.PLAY, true, 40, "Lecture / pause", onToggle);
        IconButton next = new IconButton(IconButton.Glyph.NEXT, false, 32, "Composer un nouveau morceau", onNext);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
        buttons.setOpaque(false);
        buttons.add(Box.createHorizontalStrut(32)); // équilibre visuel avec le bouton « suivant »
        buttons.add(playButton);
        buttons.add(next);

        JPanel progressRow = new JPanel(new BorderLayout(10, 0));
        progressRow.setOpaque(false);
        progressRow.add(elapsed, BorderLayout.WEST);
        progressRow.add(progress, BorderLayout.CENTER);
        progressRow.add(barLabel, BorderLayout.EAST);
        progressRow.setMaximumSize(new Dimension(560, 18));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        buttons.setAlignmentX(CENTER_ALIGNMENT);
        progressRow.setAlignmentX(CENTER_ALIGNMENT);
        center.add(buttons);
        center.add(Box.createVerticalStrut(6));
        center.add(progressRow);

        // droite : volume + export
        JPanel right = new JPanel(new GridBagLayout()); // centrage vertical, même sans le bouton d'export
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(330, 58));
        Slider volume = new Slider(0.8, onVolume);
        volume.setPreferredSize(new Dimension(110, 20));
        volume.setToolTipText("Volume");
        exportButton = new PillButton("Exporter en .mid", false, onExport);
        exportButton.setToolTipText("Enregistre toute la musique composée pendant la session");
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 5, 0, 5);
        c.weightx = 1;
        c.anchor = GridBagConstraints.EAST;
        right.add(Theme.label("Volume", Font.PLAIN, 11, Theme.MUTED), c);
        c.weightx = 0;
        right.add(volume, c);
        right.add(exportButton, c);

        add(left, BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    public void setPlaying(boolean playing) {
        playButton.setGlyph(playing ? IconButton.Glyph.PAUSE : IconButton.Glyph.PLAY);
    }

    public void setTrack(String trackTitle, String sub, Mood mood) {
        title.setText(trackTitle);
        subtitle.setText(sub);
        cover.setTrack(trackTitle, mood);
    }

    /** Affiche une vraie pochette (mode Écouter). */
    public void setCoverImage(Image img) {
        cover.setImage(img);
    }

    /** Adapte la barre au mode : export MIDI et progression non manipulable en mode Composer. */
    public void setListenMode(boolean listen) {
        exportButton.setVisible(!listen);
        progress.setInteractive(listen);
    }

    public void setAccent(Color c) {
        progress.setFill(c);
    }

    public void setProgress(double fraction, String time, String bar) {
        progress.setValue(fraction);
        elapsed.setText(time);
        barLabel.setText(bar);
    }
}
