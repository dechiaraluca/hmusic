package hmusic.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Champ mot de passe arrondi (même style que {@link HintField}). */
public class HintPasswordField extends JPasswordField {

    private final String hint;

    public HintPasswordField(String hint) {
        this.hint = hint;
        setOpaque(false);
        setFont(Theme.font(Font.PLAIN, 15));
        setForeground(Theme.TEXT);
        setCaretColor(Theme.TEXT);
        setSelectionColor(Theme.alpha(Theme.ACCENT, 140));
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
        if (getPassword().length == 0) {
            g.setFont(Theme.font(Font.PLAIN, 15));
            g.setColor(Theme.MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(hint, getInsets().left, (h - fm.getHeight()) / 2 + fm.getAscent());
        }
        g.dispose();
        Theme.texteNet(g0);
        super.paintComponent(g0);
    }
}
