package hmusic.ui;

import hmusic.model.Mood;
import hmusic.model.Sector;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Catégories d'humeur du mode Écouter : une pastille par famille d'émotion (les 8 secteurs du pavé).
 * Un clic lance les morceaux du catalogue qui correspondent à cette humeur.
 * Les pastilles sont dessinées sur 2 lignes de 4 pour tenir dans la colonne de droite.
 */
public class CategoriesHumeur extends JComponent {

    private static final int COLONNES = 4, H = 32, ESPACE = 8;

    private Sector active;   // catégorie en cours d'écoute (null : aucune)
    private int survol = -1;

    public CategoriesHumeur(Consumer<Sector> onChoix) {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter souris = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = indexA(e.getPoint());
                if (i != survol) {
                    survol = i;
                    setToolTipText(i < 0 ? null : "Écouter des morceaux « " + Sector.RING[i].label().toLowerCase(Locale.FRANCE)
                            + " » : " + Sector.RING[i].description().toLowerCase(Locale.FRANCE));
                    repaint();
                }
            }

            @Override public void mouseExited(MouseEvent e) {
                survol = -1;
                repaint();
            }

            @Override public void mouseClicked(MouseEvent e) {
                int i = indexA(e.getPoint());
                if (i >= 0) onChoix.accept(Sector.RING[i]);
            }
        };
        addMouseListener(souris);
        addMouseMotionListener(souris);
    }

    /** Met en avant la catégorie en cours (null pour aucune). */
    public void setActive(Sector s) {
        active = s;
        repaint();
    }

    @Override public Dimension getPreferredSize() {
        int lignes = (Sector.RING.length + COLONNES - 1) / COLONNES;
        return new Dimension(100, lignes * H + (lignes - 1) * ESPACE);
    }

    /** Rectangle de la pastille n° i. */
    private Rectangle zone(int i) {
        int largeur = (getWidth() - (COLONNES - 1) * ESPACE) / COLONNES;
        int col = i % COLONNES, ligne = i / COLONNES;
        return new Rectangle(col * (largeur + ESPACE), ligne * (H + ESPACE), largeur, H);
    }

    private int indexA(Point p) {
        for (int i = 0; i < Sector.RING.length; i++) if (zone(i).contains(p)) return i;
        return -1;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme.hints(g);
        g.setFont(Theme.font(Font.BOLD, 12.5f));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < Sector.RING.length; i++) {
            Sector s = Sector.RING[i];
            Rectangle r = zone(i);
            RoundRectangle2D forme = new RoundRectangle2D.Double(r.x, r.y, r.width - 1, r.height - 1, H, H);
            boolean choisie = s == active;
            Color fg;
            if (choisie) {
                g.setColor(s.color());
                g.fill(forme);
                fg = Theme.readableOn(s.color());
            } else {
                g.setColor(i == survol ? Theme.CARD_HI : Theme.CARD);
                g.fill(forme);
                g.setColor(i == survol ? Mood.mix(s.color(), Theme.BORDER, 0.4) : Theme.BORDER);
                g.draw(forme);
                fg = i == survol ? Theme.TEXT : new Color(0xD7DAE0);
                // pastille de couleur à gauche du nom
                g.setColor(s.color());
                g.fill(new Ellipse2D.Double(r.x + 12, r.y + (H - 9) / 2.0, 9, 9));
            }
            int tx = choisie ? r.x + 12 : r.x + 12 + 9 + 7;
            String texte = Theme.fit(fm, s.label(), r.x + r.width - 10 - tx);
            g.setColor(fg);
            g.drawString(texte, tx, r.y + (H - fm.getHeight()) / 2 + fm.getAscent());
        }
        g.dispose();
    }
}
