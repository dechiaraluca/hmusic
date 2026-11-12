package hmusic.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.DoubleConsumer;

/** Barre fine (progression ou volume), éventuellement manipulable à la souris. */
public class Slider extends JComponent {

    private double value;
    private final DoubleConsumer onChange;
    private Color fill = Theme.TEXT;
    private boolean hover;
    private boolean interactive;

    /** @param onChange null pour une barre non interactive */
    public Slider(double value, DoubleConsumer onChange) {
        this.value = value;
        this.onChange = onChange;
        setPreferredSize(new Dimension(120, 16));
        if (onChange != null) {
            setInteractive(true);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = interactive; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { drag(e); }
                @Override public void mouseDragged(MouseEvent e) { drag(e); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }
    }

    /** Active ou désactive la manipulation à la souris. */
    public void setInteractive(boolean b) {
        interactive = b && onChange != null;
        if (!interactive) hover = false;
        setCursor(Cursor.getPredefinedCursor(interactive ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        repaint();
    }

    private void drag(MouseEvent e) {
        if (!interactive) return;
        int pad = 6;
        setValue((e.getX() - pad) / (double) Math.max(1, getWidth() - 2 * pad));
        onChange.accept(value);
    }

    public void setValue(double v) {
        value = Math.max(0, Math.min(1, v));
        repaint();
    }

    public double getValue() { return value; }

    public void setFill(Color c) {
        fill = c;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hints(g);
        int pad = 6;
        double w = getWidth() - 2 * pad;
        double y = getHeight() / 2.0 - 2;
        g.setColor(Theme.BORDER);
        g.fill(new RoundRectangle2D.Double(pad, y, w, 4, 4, 4));
        g.setColor(hover ? fill : Theme.alpha(fill, 220));
        g.fill(new RoundRectangle2D.Double(pad, y, w * value, 4, 4, 4));
        if (hover) {
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(pad + w * value - 6, getHeight() / 2.0 - 6, 12, 12));
        }
        g.dispose();
    }
}
