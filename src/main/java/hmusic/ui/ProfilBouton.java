package hmusic.ui;

import hmusic.utilisateur.Admin;
import hmusic.utilisateur.OptionMenu;
import hmusic.utilisateur.Utilisateur;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import java.util.function.Consumer;

/** Bouton « profil » en haut à droite : avatar, identifiant, rôle. Un clic ouvre le menu de l'utilisateur. */
public class ProfilBouton extends JComponent {

    private final Utilisateur utilisateur;
    private boolean hover;

    public ProfilBouton(Utilisateur utilisateur, Consumer<OptionMenu> onOption) {
        this.utilisateur = utilisateur;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Ton menu");
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                JPopupMenu menu = Sidebar.menu(utilisateur, onOption);
                menu.show(ProfilBouton.this, getWidth() - menu.getPreferredSize().width, getHeight() + 6);
            }
        });
    }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Theme.font(Font.BOLD, 13.5f));
        return new Dimension(Math.max(170, fm.stringWidth(utilisateur.identifiant()) + 100), 46);
    }

    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hints(g);
        int w = getWidth(), h = getHeight();
        g.setColor(hover ? Theme.BORDER : Theme.CARD_HI);
        g.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, h, h));

        // avatar : initiale dans un disque (orange = admin, turquoise = auditeur)
        Color c = utilisateur instanceof Admin ? new Color(0xFF7A59) : new Color(0x2EC4B6);
        int d = h - 12;
        g.setColor(c);
        g.fill(new Ellipse2D.Double(6, 6, d, d));
        String initiale = utilisateur.identifiant().substring(0, 1).toUpperCase(Locale.ROOT);
        g.setFont(Theme.font(Font.BOLD, 15));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.readableOn(c));
        g.drawString(initiale, 6 + (d - fm.stringWidth(initiale)) / 2f, 6 + (d - fm.getHeight()) / 2f + fm.getAscent());

        int tx = 6 + d + 10;
        g.setFont(Theme.font(Font.BOLD, 13.5f));
        g.setColor(Theme.TEXT);
        g.drawString(Theme.fit(g.getFontMetrics(), utilisateur.identifiant(), w - tx - 28), tx, h / 2 - 1);
        g.setFont(Theme.font(Font.PLAIN, 11));
        g.setColor(Theme.MUTED);
        g.drawString(utilisateur.role(), tx, h / 2 + 13);

        // petite flèche vers le bas
        Path2D fleche = new Path2D.Double();
        fleche.moveTo(w - 24, h / 2.0 - 2);
        fleche.lineTo(w - 16, h / 2.0 - 2);
        fleche.lineTo(w - 20, h / 2.0 + 3);
        fleche.closePath();
        g.setColor(Theme.TEXT);
        g.fill(fleche);
        g.dispose();
    }
}
