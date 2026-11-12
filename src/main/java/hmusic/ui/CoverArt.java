package hmusic.ui;

import hmusic.model.Mood;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/**
 * Pochette générative : chaque morceau a une pochette unique,
 * calculée à partir de son titre (graine aléatoire) et de la couleur de l'humeur.
 */
public class CoverArt extends JComponent {

    private Color c1 = Theme.ACCENT;
    private Color c2 = Theme.ACCENT;
    private long seed;

    public CoverArt() {
        setPreferredSize(new Dimension(58, 58));
    }

    private Image image;

    public void setTrack(String title, Mood mood) {
        seed = title == null ? 0 : title.hashCode();
        c1 = mood.color();
        c2 = mood.color();
        image = null;
        repaint();
    }

    /** Affiche une vraie pochette (null = revenir à la pochette générative). */
    public void setImage(Image img) {
        image = img;
        repaint();
    }

    /** Variante à deux couleurs (utilisée pour les voyages : départ → arrivée). */
    public void setColors(Color from, Color to, long seed) {
        this.c1 = from;
        this.c2 = to;
        this.seed = seed;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hints(g);
        int s = Math.min(getWidth(), getHeight());
        RoundRectangle2D rr = new RoundRectangle2D.Double(0, 0, s, s, s * 0.22, s * 0.22);
        g.clip(rr);
        if (image != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(image, 0, 0, s, s, null);
            g.dispose();
            return;
        }
        g.setPaint(new GradientPaint(0, 0, Mood.mix(c1, Color.WHITE, 0.18), s, s, Mood.mix(c2, Theme.BG, 0.55)));
        g.fill(rr);

        Random r = new Random(seed);
        g.setStroke(new BasicStroke(Math.max(1f, s / 40f)));
        for (int i = 0; i < 4; i++) {
            double rad = s * (0.2 + r.nextDouble() * 0.6);
            double x = r.nextDouble() * s, y = r.nextDouble() * s;
            g.setColor(Theme.alpha(Color.WHITE, 40 + r.nextInt(50)));
            if (r.nextBoolean()) g.draw(new Ellipse2D.Double(x - rad, y - rad, rad * 2, rad * 2));
            else g.fill(new Ellipse2D.Double(x - rad / 3, y - rad / 3, rad / 1.5, rad / 1.5));
        }
        Path2D wave = new Path2D.Double();
        double freq = 2 + r.nextDouble() * 3, amp = s * (0.05 + r.nextDouble() * 0.1), base = s * (0.55 + r.nextDouble() * 0.25);
        for (int x = 0; x <= s; x++) {
            double y = base + Math.sin(x / (double) s * Math.PI * freq) * amp;
            if (x == 0) wave.moveTo(x, y); else wave.lineTo(x, y);
        }
        g.setColor(Theme.alpha(Color.WHITE, 170));
        g.setStroke(new BasicStroke(Math.max(1.5f, s / 26f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(wave);
        g.dispose();
    }
}
