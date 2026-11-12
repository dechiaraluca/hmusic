package hmusic.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.List;
import java.util.function.Function;

/** Briques communes des fenêtres de dialogue (style sombre de l'application). */
public final class Dialogues {

    public static final Color ERREUR = new Color(0xFF6B6B);

    private Dialogues() {}

    /** Fenêtre modale : titre, sous-titre, contenu au centre et boutons en bas à droite. */
    public static JDialog fenetre(Window owner, String titre, String sousTitre, JComponent centre, JComponent... boutons) {
        JDialog d = new JDialog(owner, titre, Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(22, 24, 20, 24));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(Theme.label(titre, Font.BOLD, 22, Theme.TEXT));
        if (sousTitre != null) {
            header.add(Box.createVerticalStrut(4));
            header.add(Theme.label(sousTitre, Font.PLAIN, 13, Theme.MUTED));
        }
        root.add(header, BorderLayout.NORTH);
        root.add(centre, BorderLayout.CENTER);

        JPanel bas = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bas.setOpaque(false);
        for (JComponent b : boutons) bas.add(b);
        root.add(bas, BorderLayout.SOUTH);

        d.setContentPane(root);
        d.setIconImages(List.of(Theme.appIcon(16), Theme.appIcon(32)));
        return d;
    }

    /** Zone de défilement sombre. */
    public static JScrollPane defilement(JComponent contenu) {
        JScrollPane sp = new JScrollPane(contenu);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Theme.BG);
        Theme.darkScrollBar(sp.getVerticalScrollBar());
        return sp;
    }

    /** Petit titre de section en majuscules. */
    public static JLabel section(String texte) {
        JLabel l = Theme.label(texte, Font.BOLD, 11, Theme.MUTED);
        l.setBorder(new EmptyBorder(4, 2, 6, 0));
        return l;
    }

    /**
     * Liste sombre à deux lignes par élément (titre + détail) avec une pastille de couleur.
     *
     * @param <T> type des éléments affichés
     */
    public static final class Liste<T> extends JList<T> {

        public Liste(Function<T, String> titre, Function<T, String> detail, Function<T, Color> pastille) {
            setBackground(Theme.BG);
            setFixedCellHeight(48);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            setCellRenderer(new Rendu<>(titre, detail, pastille));
        }

        public void setElements(List<T> elements) {
            DefaultListModel<T> model = new DefaultListModel<>();
            elements.forEach(model::addElement);
            setModel(model);
        }
    }

    private static final class Rendu<T> extends JComponent implements ListCellRenderer<T> {
        private final Function<T, String> titre, detail;
        private final Function<T, Color> pastille;
        private T valeur;
        private boolean selection;

        Rendu(Function<T, String> titre, Function<T, String> detail, Function<T, Color> pastille) {
            this.titre = titre;
            this.detail = detail;
            this.pastille = pastille;
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends T> list, T value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            valeur = value;
            selection = isSelected;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hints(g);
            int w = getWidth(), h = getHeight();
            g.setColor(selection ? Theme.CARD_HI : Theme.BG);
            g.fillRect(0, 0, w, h);
            g.setColor(pastille.apply(valeur));
            g.fill(new Ellipse2D.Double(14, h / 2.0 - 6, 12, 12));
            g.setFont(Theme.font(Font.BOLD, 13));
            g.setColor(Theme.TEXT);
            g.drawString(Theme.fit(g.getFontMetrics(), titre.apply(valeur), w - 50), 38, h / 2 - 3);
            g.setFont(Theme.font(Font.PLAIN, 11.5f));
            g.setColor(Theme.MUTED);
            g.drawString(Theme.fit(g.getFontMetrics(), detail.apply(valeur), w - 50), 38, h / 2 + 13);
            g.dispose();
        }
    }
}
