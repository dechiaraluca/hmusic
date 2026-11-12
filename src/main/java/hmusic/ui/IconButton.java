package hmusic.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** Bouton rond avec un pictogramme dessiné en vectoriel (pas d'image à charger). */
public class IconButton extends JComponent {

    public enum Glyph { PLAY, PAUSE, NEXT }

    private Glyph glyph;
    private final boolean primary;
    private final Runnable action;
    private boolean hover, pressed;

    public IconButton(Glyph glyph, boolean primary, int size, String tooltip, Runnable action) {
        this.glyph = glyph;
        this.primary = primary;
        this.action = action;
        setPreferredSize(new Dimension(size, size));
        setToolTipText(tooltip);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; pressed = false; repaint(); }
            @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
            @Override public void mouseReleased(MouseEvent e) {
                boolean fire = pressed && contains(e.getPoint());
                pressed = false;
                repaint();
                if (fire && IconButton.this.action != null) IconButton.this.action.run();
            }
        });
    }

    public void setGlyph(Glyph g) {
        glyph = g;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hints(g);
        double s = Math.min(getWidth(), getHeight()) * (pressed ? 0.94 : 1.0);
        double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
        Color fg;
        if (primary) {
            g.setColor(hover ? Color.WHITE : new Color(0xE8E9EC));
            g.fill(new Ellipse2D.Double(cx - s / 2, cy - s / 2, s, s));
            fg = Theme.BG;
        } else {
            fg = hover ? Theme.TEXT : Theme.MUTED;
        }
        g.setColor(fg);
        double u = s * (primary ? 0.5 : 0.62); // taille du pictogramme
        switch (glyph) {
            case PLAY -> {
                Path2D p = new Path2D.Double();
                p.moveTo(cx - u * 0.30, cy - u * 0.42);
                p.lineTo(cx + u * 0.45, cy);
                p.lineTo(cx - u * 0.30, cy + u * 0.42);
                p.closePath();
                g.fill(p);
            }
            case PAUSE -> {
                double w = u * 0.22, h = u * 0.8;
                g.fill(new RoundRectangle2D.Double(cx - u * 0.3, cy - h / 2, w, h, 3, 3));
                g.fill(new RoundRectangle2D.Double(cx + u * 0.08, cy - h / 2, w, h, 3, 3));
            }
            case NEXT -> {
                Path2D p = new Path2D.Double();
                p.moveTo(cx - u * 0.4, cy - u * 0.38);
                p.lineTo(cx + u * 0.22, cy);
                p.lineTo(cx - u * 0.4, cy + u * 0.38);
                p.closePath();
                g.fill(p);
                g.fill(new Rectangle2D.Double(cx + u * 0.24, cy - u * 0.38, u * 0.14, u * 0.76));
            }
        }
        g.dispose();
    }
}
