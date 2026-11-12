package hmusic.ui;

import hmusic.engine.EngineListener;
import hmusic.engine.MoodEngine;
import hmusic.model.Mood;
import hmusic.model.MusicalProfile;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Visualiseur réactif : chaque note réellement jouée par le moteur fait naître une particule.
 * <ul>
 *   <li>mélodie : bulles qui montent (position horizontale = hauteur de la note)</li>
 *   <li>nappe : grands halos diffus</li>
 *   <li>basse : ondes concentriques</li>
 *   <li>arpège : petites étincelles</li>
 *   <li>grosse caisse : l'aura centrale bat ; caisse claire : éclats</li>
 * </ul>
 */
public class Visualizer extends JComponent implements EngineListener {

    private static final class Particle {
        double x, y, vx, vy, r, vr, life = 1, decay;
        int kind;
        Color color;
    }

    private static final int MAX_PARTICLES = 500;

    private final List<Particle> particles = new ArrayList<>();
    private final ConcurrentLinkedQueue<int[]> incoming = new ConcurrentLinkedQueue<>();
    private final Random rnd = new Random();

    private Mood mood = Mood.NEUTRAL;
    private Color color = mood.color();
    private MusicalProfile profile = MusicalProfile.from(mood);
    private String title = "Prêt à composer";
    private String subtitle = "Choisis une humeur sur le pavé, une station, ou décris ce que tu ressens.";
    private boolean playing;
    private double kick, time;

    public Visualizer() {
        setOpaque(false);
        setPreferredSize(new Dimension(520, 420));
        new Timer(16, e -> animate()).start();
    }

    /** Appelé depuis le thread audio : on se contente de mettre l'évènement en file. */
    @Override
    public void onNote(int channel, int pitch, int velocity) {
        if (incoming.size() < 400) incoming.add(new int[]{channel, pitch, velocity});
    }

    public void setMood(Mood m) {
        mood = m;
        profile = MusicalProfile.from(m);
    }

    public void setTrack(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
        repaint();
    }

    public void setPlaying(boolean p) {
        playing = p;
        repaint();
    }

    private void animate() {
        time += 0.016;
        color = Mood.mix(color, mood.color(), 0.08);
        int w = getWidth(), h = getHeight();
        int[] ev;
        while ((ev = incoming.poll()) != null) {
            if (w > 0 && h > 0) spawn(ev[0], ev[1], ev[2], w, h);
        }
        kick *= 0.9;
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.x += p.vx;
            p.y += p.vy;
            p.r += p.vr;
            p.life -= p.decay;
            if (p.life <= 0 || p.r <= 0) it.remove();
        }
        if (isShowing()) repaint();
    }

    private void spawn(int ch, int pitch, int vel, int w, int h) {
        if (ch == MoodEngine.CH_DRUMS) {
            if (pitch == 36) {
                kick = Math.max(kick, vel / 127.0);
            } else if (pitch == 38 || pitch == 39 || pitch == 40) {
                for (int i = 0; i < 8; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, sp = 2 + rnd.nextDouble() * 3;
                    add(w / 2.0, h * 0.45, Math.cos(a) * sp, Math.sin(a) * sp, 2, 0, 0.035, 3, Color.WHITE);
                }
            }
            return;
        }
        if (particles.size() > MAX_PARTICLES) return;
        double px = w * (0.08 + 0.84 * Math.max(0, Math.min(1, (pitch - 40) / 50.0)));
        switch (ch) {
            case MoodEngine.CH_MELODY -> add(px, h * 0.8, (rnd.nextDouble() - 0.5) * 0.4, -(1.0 + vel / 70.0),
                    4 + vel / 20.0, 0, 0.007, 0, Mood.mix(color, Color.WHITE, 0.45));
            case MoodEngine.CH_PAD -> add(w * (0.15 + rnd.nextDouble() * 0.7), h * (0.3 + rnd.nextDouble() * 0.3),
                    (rnd.nextDouble() - 0.5) * 0.3, -0.12, 70 + rnd.nextDouble() * 90, 0.15, 0.0035, 1, color);
            case MoodEngine.CH_BASS -> add(w / 2.0, h * 0.45, 0, 0, 30, 3.2 + vel / 50.0, 0.018, 2, color);
            case MoodEngine.CH_ARP -> add(px, h * 0.65 + (rnd.nextDouble() - 0.5) * 40, (rnd.nextDouble() - 0.5) * 0.8,
                    -0.6 - rnd.nextDouble(), 2.2, 0, 0.016, 3, Mood.mix(color, Color.WHITE, 0.7));
            default -> { }
        }
    }

    private void add(double x, double y, double vx, double vy, double r, double vr, double decay, int kind, Color c) {
        Particle p = new Particle();
        p.x = x; p.y = y; p.vx = vx; p.vy = vy; p.r = r; p.vr = vr; p.decay = decay; p.kind = kind; p.color = c;
        particles.add(p);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hints(g);
        int w = getWidth(), h = getHeight();
        if (w < 10 || h < 10) {
            g.dispose();
            return;
        }
        Shape card = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 28, 28);
        g.setClip(card);
        g.setColor(Theme.CARD);
        g.fillRect(0, 0, w, h);

        // halo de fond
        float radius = (float) (Math.max(w, h) * 0.8);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.5, h * 0.42), radius, new float[]{0f, 1f},
                new Color[]{Theme.alpha(color, 85 + 60 * kick), Theme.alpha(color, 0)}));
        g.fillRect(0, 0, w, h);

        // ondes « aurores » dont la vitesse suit le tempo
        double speed = profile.bpm() / 60.0;
        double e01 = (mood.energy() + 1) / 2;
        for (int k = 0; k < 3; k++) {
            Path2D path = new Path2D.Double();
            double amp = h * (0.03 + 0.06 * e01) * (1 + kick * 0.35);
            double freq = 0.006 + k * 0.003;
            double phase = time * speed * (1 + k * 0.3) + k;
            double baseY = h * (0.38 + k * 0.08);
            for (int x = 0; x <= w; x += 6) {
                double y = baseY + Math.sin(x * freq + phase) * amp + Math.sin(x * freq * 2.3 - phase * 0.7) * amp * 0.3;
                if (x == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            g.setStroke(new BasicStroke(2f));
            g.setColor(Theme.alpha(Mood.mix(color, Color.WHITE, 0.3), 70 - k * 18));
            g.draw(path);
        }

        // aura centrale
        double ar = Math.min(w, h) * (0.11 + 0.05 * kick + 0.03 * mood.intensity());
        Point2D center = new Point2D.Double(w / 2.0, h * 0.45);
        g.setPaint(new RadialGradientPaint(center, (float) (ar * 2), new float[]{0f, 0.45f, 1f},
                new Color[]{Theme.alpha(Mood.mix(color, Color.WHITE, 0.45), 210), Theme.alpha(color, 90), Theme.alpha(color, 0)}));
        g.fill(new Ellipse2D.Double(center.getX() - ar * 2, center.getY() - ar * 2, ar * 4, ar * 4));

        // particules
        for (Particle p : particles) {
            double life = Math.max(0, p.life);
            switch (p.kind) {
                case 0 -> {
                    g.setColor(Theme.alpha(p.color, life * 55));
                    g.fill(new Ellipse2D.Double(p.x - p.r * 2.2, p.y - p.r * 2.2, p.r * 4.4, p.r * 4.4));
                    g.setColor(Theme.alpha(p.color, life * 230));
                    g.fill(new Ellipse2D.Double(p.x - p.r, p.y - p.r, p.r * 2, p.r * 2));
                }
                case 1 -> {
                    g.setPaint(new RadialGradientPaint(new Point2D.Double(p.x, p.y), (float) p.r, new float[]{0f, 1f},
                            new Color[]{Theme.alpha(p.color, life * 45), Theme.alpha(p.color, 0)}));
                    g.fill(new Ellipse2D.Double(p.x - p.r, p.y - p.r, p.r * 2, p.r * 2));
                }
                case 2 -> {
                    g.setStroke(new BasicStroke(2f));
                    g.setColor(Theme.alpha(Mood.mix(p.color, Color.WHITE, 0.3), life * 130));
                    g.draw(new Ellipse2D.Double(p.x - p.r, p.y - p.r, p.r * 2, p.r * 2));
                }
                default -> {
                    g.setColor(Theme.alpha(p.color, life * 255));
                    g.fill(new Ellipse2D.Double(p.x - p.r, p.y - p.r, p.r * 2, p.r * 2));
                }
            }
        }

        // voile pour la lisibilité du texte
        g.setPaint(new GradientPaint(0, (float) (h * 0.55), Theme.alpha(Theme.CARD, 0), 0, h, Theme.alpha(Theme.CARD, 240)));
        g.fillRect(0, (int) (h * 0.55), w, h);

        // pastilles d'information
        int cx = 22;
        for (String chip : new String[]{profile.bpm() + " BPM", "mode " + profile.scale().label(),
                "ambiance " + profile.instruments().label()}) {
            cx += drawChip(g, chip, cx, 22) + 8;
        }
        g.setFont(Theme.font(Font.BOLD, 11));
        String state = playing ? "EN LECTURE" : "EN PAUSE";
        int sw = g.getFontMetrics().stringWidth(state);
        g.setColor(playing ? Mood.mix(color, Color.WHITE, 0.5) : Theme.MUTED);
        g.drawString(state, w - 22 - sw, 39);
        double dot = playing ? 8 * (1 + kick * 0.5) : 8;
        g.fill(new Ellipse2D.Double(w - 34 - sw - dot / 2, 35 - dot / 2, dot, dot));

        // titre du morceau
        int x = 28, y = h - 30;
        g.setFont(Theme.font(Font.PLAIN, 14));
        g.setColor(Theme.MUTED);
        g.drawString(Theme.fit(g.getFontMetrics(), subtitle, w - 56), x, y);
        g.setFont(Theme.font(Font.BOLD, 34));
        g.setColor(Theme.TEXT);
        g.drawString(Theme.fit(g.getFontMetrics(), title, w - 56), x, y - 28);

        g.setClip(null);
        g.setColor(Theme.BORDER);
        g.setStroke(new BasicStroke(1f));
        g.draw(card);
        g.dispose();
    }

    private int drawChip(Graphics2D g, String text, int x, int y) {
        g.setFont(Theme.font(Font.BOLD, 11.5f));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 22, h = 26;
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        g.setColor(Theme.TEXT);
        g.drawString(text, x + 11, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        return w;
    }
}
