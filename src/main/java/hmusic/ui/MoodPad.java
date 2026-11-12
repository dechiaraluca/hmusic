package hmusic.ui;

import hmusic.model.Mood;
import hmusic.model.Sector;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Le « pavé d'humeur » : un plan 2D (valence horizontale, énergie verticale)
 * sur lequel on fait glisser son humeur. Clic droit = choisir une destination.
 */
public class MoodPad extends JComponent {

    public interface Listener {
        void moodPicked(Mood mood, boolean adjusting);

        void destinationPicked(Mood target);
    }

    private final Listener listener;
    private Mood mood = Mood.NEUTRAL;
    private Mood target;
    private List<Mood> memories = List.of();
    private BufferedImage field;
    private float pulse;

    public MoodPad(Listener listener) {
        this.listener = listener;
        setOpaque(false);
        setPreferredSize(new Dimension(400, 400));
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        setToolTipText("<html>Clic / glisser : ton humeur<br>Clic droit : une destination (voyage guidé)</html>");

        MouseAdapter ma = new MouseAdapter() {
            private boolean dragging;

            @Override public void mousePressed(MouseEvent e) {
                Mood m = toMood(e.getPoint());
                if (m == null) return;
                if (SwingUtilities.isRightMouseButton(e)) {
                    MoodPad.this.listener.destinationPicked(m);
                    return;
                }
                dragging = true;
                pick(m, true);
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (!dragging) return;
                Mood m = toMood(e.getPoint());
                if (m != null) pick(m, true);
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (!dragging) return;
                dragging = false;
                Mood m = toMood(e.getPoint());
                if (m != null) pick(m, false);
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);

        new Timer(33, e -> {
            if (pulse > 0.01f) {
                pulse *= 0.85f;
                repaint();
            }
        }).start();
    }

    private void pick(Mood m, boolean adjusting) {
        mood = m;
        repaint();
        listener.moodPicked(m, adjusting);
    }

    public void setMood(Mood m) {
        mood = m;
        repaint();
    }

    public void setTarget(Mood t) {
        target = t;
        repaint();
    }

    public void setMemories(List<Mood> list) {
        memories = List.copyOf(list);
        repaint();
    }

    /** Petit battement visuel synchronisé sur la grosse caisse. */
    public void pulse(float amount) {
        pulse = Math.max(pulse, amount);
        repaint();
    }

    // ------------------------------------------------------------------ géométrie

    private Rectangle square() {
        int s = Math.min(getWidth(), getHeight()) - 4;
        return new Rectangle((getWidth() - s) / 2, (getHeight() - s) / 2, s, s);
    }

    private Mood toMood(Point p) {
        Rectangle r = square();
        if (r.width <= 0) return null;
        double v = (p.x - r.x) / (double) r.width * 2 - 1;
        double e = 1 - (p.y - r.y) / (double) r.height * 2;
        return new Mood(v, e);
    }

    private Point2D toPoint(Mood m) {
        Rectangle r = square();
        return new Point2D.Double(r.x + (m.valence() + 1) / 2 * r.width, r.y + (1 - m.energy()) / 2 * r.height);
    }

    /** Pré-calcule le dégradé de couleurs du plan (recalculé seulement si la taille change). */
    private void ensureField(int s) {
        if (field != null && field.getWidth() == s) return;
        field = new BufferedImage(s, s, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                Mood m = new Mood(x / (s - 1.0) * 2 - 1, 1 - y / (s - 1.0) * 2);
                Color c = Mood.mix(Theme.BG, m.color(), 0.18 + 0.5 * m.intensity());
                field.setRGB(x, y, c.getRGB());
            }
        }
    }

    // ------------------------------------------------------------------ dessin

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hints(g);
        Rectangle r = square();
        if (r.width < 40) {
            g.dispose();
            return;
        }
        ensureField(r.width);
        Shape card = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 28, 28);
        g.setClip(card);
        g.drawImage(field, r.x, r.y, null);

        double cx = r.getCenterX(), cy = r.getCenterY(), half = r.width / 2.0;

        // grille
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 255, 255, 26));
        g.draw(new Line2D.Double(r.x, cy, r.x + r.width, cy));
        g.draw(new Line2D.Double(cx, r.y, cx, r.y + r.height));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{3f, 5f}, 0f));
        for (double k : new double[]{0.2, 0.6}) {
            g.draw(new Ellipse2D.Double(cx - half * k, cy - half * k, half * k * 2, half * k * 2));
        }

        // libellés des secteurs
        g.setFont(Theme.font(Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < Sector.RING.length; i++) {
            Sector s = Sector.RING[i];
            double a = Math.toRadians(i * 45);
            double lx = cx + Math.cos(a) * half * 0.74, ly = cy - Math.sin(a) * half * 0.74;
            boolean current = mood.sector() == s;
            g.setColor(current ? Color.WHITE : new Color(255, 255, 255, 120));
            g.drawString(s.label(), (float) (lx - fm.stringWidth(s.label()) / 2.0), (float) (ly + fm.getAscent() / 2.0 - 2));
        }

        // légendes des axes
        g.setFont(Theme.font(Font.PLAIN, 10.5f));
        fm = g.getFontMetrics();
        g.setColor(new Color(255, 255, 255, 90));
        String top = "+ ÉNERGIE", bottom = "CALME", left = "DÉSAGRÉABLE", right = "AGRÉABLE";
        g.drawString(top, (float) (cx - fm.stringWidth(top) / 2.0), r.y + 18);
        g.drawString(bottom, (float) (cx - fm.stringWidth(bottom) / 2.0), r.y + r.height - 10);
        g.drawString(left, r.x + 12, (float) cy + 26);
        g.drawString(right, r.x + r.width - 12 - fm.stringWidth(right), (float) cy + 26);

        // souvenirs (journal)
        for (int i = 0; i < memories.size(); i++) {
            Point2D p = toPoint(memories.get(i));
            int a = 30 + (int) (90.0 * i / Math.max(1, memories.size()));
            g.setColor(new Color(255, 255, 255, a));
            g.fill(new Ellipse2D.Double(p.getX() - 2.5, p.getY() - 2.5, 5, 5));
        }

        Point2D p = toPoint(mood);

        // destination d'un voyage
        if (target != null) {
            Point2D t = toPoint(target);
            g.setColor(new Color(255, 255, 255, 170));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{2f, 7f}, 0f));
            g.draw(new Line2D.Double(p, t));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Double(t.getX() - 12, t.getY() - 12, 24, 24));
            g.fill(new Ellipse2D.Double(t.getX() - 3, t.getY() - 3, 6, 6));
            g.setFont(Theme.font(Font.BOLD, 11));
            g.drawString("destination", (float) t.getX() + 16, (float) t.getY() + 4);
        }

        // curseur lumineux
        Color c = mood.color();
        double glow = 46 * (1 + pulse * 0.7);
        g.setPaint(new RadialGradientPaint(p, (float) glow, new float[]{0f, 1f},
                new Color[]{Theme.alpha(Mood.mix(c, Color.WHITE, 0.4), 200), Theme.alpha(c, 0)}));
        g.fill(new Ellipse2D.Double(p.getX() - glow, p.getY() - glow, glow * 2, glow * 2));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new Ellipse2D.Double(p.getX() - 11, p.getY() - 11, 22, 22));
        g.setColor(c);
        g.fill(new Ellipse2D.Double(p.getX() - 7, p.getY() - 7, 14, 14));

        g.setClip(null);
        g.setStroke(new BasicStroke(1f));
        g.setColor(Theme.BORDER);
        g.draw(card);
        g.dispose();
    }
}
