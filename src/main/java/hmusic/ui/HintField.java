package hmusic.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Champ texte arrondi avec un texte d'exemple quand il est vide. */
public class HintField extends JTextField {

    private final String hint;

    public HintField(String hint) {
        this.hint = hint;
        setOpaque(false);
        setFont(Theme.font(Font.PLAIN, 15));
        setForeground(Theme.TEXT);
        setCaretColor(Theme.TEXT);
        setSelectionColor(Theme.alpha(Theme.ACCENT, 140));
        setSelectedTextColor(Color.WHITE);
        setBorder(new EmptyBorder(9, 20, 9, 20));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hints(g);
        int h = getHeight();
        RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, getWidth() - 1, h - 1, h, h);
        g.setColor(Theme.CARD_HI);
        g.fill(shape);
        g.setColor(hasFocus() ? Theme.MUTED : Theme.BORDER);
        g.draw(shape);
        if (getText().isEmpty()) {
            g.setFont(getFont());
            g.setColor(Theme.MUTED);
            Insets in = getInsets();
            FontMetrics fm = g.getFontMetrics();
            g.drawString(hint, in.left, (h - fm.getHeight()) / 2 + fm.getAscent());
        }
        g.dispose();
        super.paintComponent(g0);
    }
}
