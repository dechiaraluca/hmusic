package hmusic.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/** Bouton en forme de pilule, plein (couleur d'accent) ou contour. */
public class PillButton extends JComponent {

    private final String text;
    private final boolean filled;
    private final Runnable action;
    private Color accent = Theme.ACCENT;
    private boolean hover, pressed;

    public PillButton(String text, boolean filled, Runnable action) {
        this.text = text;
        this.filled = filled;
        this.action = action;
        setFont(Theme.font(Font.BOLD, 13));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; pressed = false; repaint(); }
            @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
            @Override public void mouseReleased(MouseEvent e) {
                boolean fire = pressed && contains(e.getPoint());
                pressed = false;
                repaint();
                if (fire && PillButton.this.action != null) PillButton.this.action.run();
            }
        });
    }

    public void setAccent(Color c) {
        accent = c;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(text) + 40, 40);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hints(g);
        int w = getWidth(), h = getHeight();
        double inset = pressed ? 1.5 : 0.5;
        RoundRectangle2D shape = new RoundRectangle2D.Double(inset, inset, w - 2 * inset - 1, h - 2 * inset - 1, h, h);
        Color fg;
        if (filled) {
            Color bg = hover ? accent.brighter() : accent;
            g.setColor(bg);
            g.fill(shape);
            fg = Theme.readableOn(bg);
        } else {
            g.setColor(hover ? Theme.CARD_HI : Theme.alpha(Theme.CARD_HI, 0));
            g.fill(shape);
            g.setColor(hover ? Theme.TEXT : Theme.MUTED);
            g.draw(shape);
            fg = Theme.TEXT;
        }
        g.setFont(getFont());
        g.setColor(fg);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
    }
}
