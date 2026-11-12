package hmusic.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/** Une ligne cliquable de la barre latérale (miniature + titre + sous-titre). */
public class SidebarItem extends JComponent {

    private final String title;
    private final String subtitle;
    private final CoverArt thumb = new CoverArt();
    private Runnable action;
    private boolean hover, selected;

    public SidebarItem(String title, String subtitle, Color from, Color to) {
        this.title = title;
        this.subtitle = subtitle;
        thumb.setColors(from, to, title.hashCode());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setAlignmentX(LEFT_ALIGNMENT);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                if (action != null) action.run();
            }
        });
    }

    public void setAction(Runnable action) { this.action = action; }

    public void setSelected(boolean s) {
        selected = s;
        repaint();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(220, 52); }

    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 52); }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hints(g);
        int w = getWidth(), h = getHeight();
        if (hover || selected) {
            g.setColor(selected ? Theme.CARD_HI : Theme.CARD);
            g.fill(new RoundRectangle2D.Double(0, 2, w, h - 4, 12, 12));
        }
        int s = 38;
        Graphics2D tg = (Graphics2D) g.create(8, (h - s) / 2, s, s);
        thumb.setSize(s, s);
        thumb.paint(tg);
        tg.dispose();

        int tx = 8 + s + 12;
        g.setFont(Theme.font(Font.BOLD, 13.5f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(selected ? Color.WHITE : Theme.TEXT);
        g.drawString(Theme.fit(fm, title, w - tx - 6), tx, h / 2 - 2);
        g.setFont(Theme.font(Font.PLAIN, 12));
        fm = g.getFontMetrics();
        g.setColor(Theme.MUTED);
        g.drawString(Theme.fit(fm, subtitle, w - tx - 6), tx, h / 2 + fm.getAscent());
        g.dispose();
    }
}
