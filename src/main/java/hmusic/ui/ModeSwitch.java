package hmusic.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/** Sélecteur à deux positions : « Composer » / « Écouter ». */
public class ModeSwitch extends JComponent {

    public enum Mode {
        COMPOSE("Composer", "Musique inventée en direct"),
        LISTEN("Écouter", "Vrais morceaux (Jamendo)");

        final String label, hint;

        Mode(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    private static final int W = 300, H = 46;

    private Mode mode = Mode.COMPOSE;
    private Color accent = Theme.ACCENT;
    private int hover = -1;
    private double slide; // 0 = COMPOSE, 1 = LISTEN (animé)
    private final Timer anim;

    public ModeSwitch(Consumer<Mode> onChange) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.font(Font.BOLD, 13.5f));
        anim = new Timer(15, e -> {
            double target = mode == Mode.COMPOSE ? 0 : 1;
            slide += (target - slide) * 0.3;
            if (Math.abs(target - slide) < 0.01) {
                slide = target;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                Mode m = e.getX() < getWidth() / 2 ? Mode.COMPOSE : Mode.LISTEN;
                if (m != mode) {
                    setMode(m);
                    onChange.accept(m);
                }
            }

            @Override public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int h = e.getX() < getWidth() / 2 ? 0 : 1;
                if (h != hover) {
                    hover = h;
                    setToolTipText(Mode.values()[h].hint);
                    repaint();
                }
            }
        });
    }

    public void setMode(Mode m) {
        mode = m;
        anim.start();
    }

    public void setAccent(Color c) {
        accent = c;
        repaint();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(W, H); }

    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hints(g);
        int w = getWidth(), h = getHeight();
        g.setColor(Theme.CARD_HI);
        g.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, h, h));
        double half = (w - 8) / 2.0;
        RoundRectangle2D knob = new RoundRectangle2D.Double(4 + slide * half, 4, half, h - 8, h - 8, h - 8);
        g.setColor(accent);
        g.fill(knob);

        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < 2; i++) {
            Mode m = Mode.values()[i];
            boolean selected = m == mode;
            Color fg = selected ? Theme.readableOn(accent) : (hover == i ? Theme.TEXT : Theme.MUTED);
            g.setColor(fg);
            double cx = 4 + half * i + half / 2;
            g.drawString(m.label, (float) (cx - fm.stringWidth(m.label) / 2.0), (h - fm.getHeight()) / 2f + fm.getAscent());
        }
        g.dispose();
    }
}
