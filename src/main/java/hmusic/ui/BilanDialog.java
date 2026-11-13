package hmusic.ui;

import hmusic.data.Bilan;
import hmusic.data.HistoriqueEcoute;
import hmusic.data.MoodJournal;
import hmusic.model.Mood;
import hmusic.model.Sector;
import hmusic.reco.ProfilGouts;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * « Mon bilan du mois » : une carte façon <i>Wrapped</i> (humeur dominante, calendrier des humeurs,
 * temps d'écoute, artistes, ce qui t'aide vraiment, goûts appris), exportable en image PNG.
 * Les calculs sont faits par {@link Bilan} ; cette classe ne fait que dessiner.
 */
public final class BilanDialog {

    private BilanDialog() {}

    /**
     * @param gouts goûts appris de l'auditeur, ou null (Admin)
     */
    public static void ouvrir(Window owner, MoodJournal journal, HistoriqueEcoute historique, ProfilGouts gouts) {
        Carte carte = new Carte(journal, historique, gouts);
        JDialog[] d = new JDialog[1];
        JPanel centre = new JPanel(new GridBagLayout()); // centre la carte
        centre.setOpaque(false);
        centre.add(carte);
        d[0] = Dialogues.fenetre(owner, "Mon bilan du mois", "Ton mois en un coup d'œil. Exporte-le en image pour le partager.",
                centre,
                new PillButton("‹ Mois précédent", false, () -> carte.changerMois(-1)),
                new PillButton("Mois suivant ›", false, () -> carte.changerMois(1)),
                new PillButton("Exporter en PNG", false, () -> exporter(d[0], carte)),
                new PillButton("Fermer", true, () -> d[0].dispose()));
        d[0].pack();
        d[0].setResizable(false);
        d[0].setLocationRelativeTo(owner);
        d[0].setVisible(true);
    }

    private static void exporter(Component parent, Carte carte) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exporter le bilan en image");
        fc.setFileFilter(new FileNameExtensionFilter("Image PNG (*.png)", "png"));
        fc.setSelectedFile(new File("hmusic-bilan-" + carte.mois + ".png"));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) f = new File(f.getPath() + ".png");
        // on dessine la carte en double résolution pour une image nette
        BufferedImage img = new BufferedImage(Carte.W * 2, Carte.H * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(2, 2);
        carte.peindre(g);
        g.dispose();
        try {
            ImageIO.write(img, "png", f);
            JOptionPane.showMessageDialog(parent, "Bilan enregistré dans " + f.getName() + ".", "Bilan",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Export impossible : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** La carte elle-même (taille fixe, pour qu'elle soit identique à l'écran et dans l'image). */
    private static final class Carte extends JComponent {
        static final int W = 840, H = 540, M = 34;
        private static final Color VERT = new Color(0x4ADE80);

        private final MoodJournal journal;
        private final HistoriqueEcoute historique;
        private final ProfilGouts gouts;
        private YearMonth mois = YearMonth.now();
        private Bilan bilan;

        Carte(MoodJournal journal, HistoriqueEcoute historique, ProfilGouts gouts) {
            this.journal = journal;
            this.historique = historique;
            this.gouts = gouts;
            calculer();
        }

        void changerMois(int delta) {
            YearMonth m = mois.plusMonths(delta);
            if (m.isAfter(YearMonth.now())) return; // pas de bilan pour le futur
            mois = m;
            calculer();
            repaint();
        }

        private void calculer() {
            bilan = Bilan.calculer(mois, journal.entries(), historique.ecoutes());
        }

        @Override public Dimension getPreferredSize() { return new Dimension(W, H); }

        @Override protected void paintComponent(Graphics g) {
            peindre((Graphics2D) g.create());
        }

        void peindre(Graphics2D g) {
            Theme.hints(g);
            Theme.texteNet(g);
            Color c = bilan.dominante().map(Sector::color).orElse(Theme.ACCENT);
            Color c2 = Mood.mix(c, Theme.ACCENT, 0.6);

            // fond : carte sombre éclairée par la couleur de l'humeur du mois
            RoundRectangle2D fond = new RoundRectangle2D.Double(0, 0, W, H, 32, 32);
            g.setClip(fond);
            g.setColor(new Color(0x111318));
            g.fillRect(0, 0, W, H);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(W * 0.12, H * 0.1), W * 0.7f, new float[]{0f, 1f},
                    new Color[]{Theme.alpha(c, 150), Theme.alpha(c, 0)}));
            g.fillRect(0, 0, W, H);
            g.setPaint(new RadialGradientPaint(new Point2D.Double(W * 0.95, H * 0.95), W * 0.6f, new float[]{0f, 1f},
                    new Color[]{Theme.alpha(c2, 110), Theme.alpha(c2, 0)}));
            g.fillRect(0, 0, W, H);

            peindreGauche(g, c);
            peindreDroite(g, c);
            g.setClip(null);
        }

        // ---------------------------------------------------------------- colonne de gauche

        private void peindreGauche(Graphics2D g, Color c) {
            g.drawImage(Theme.appIcon(22), M, M - 4, null);
            texte(g, "hmusic  ·  TON BILAN", Font.BOLD, 12, Theme.MUTED, M + 30, M + 12);
            String nomMois = mois.getMonth().getDisplayName(TextStyle.FULL, Locale.FRANCE);
            nomMois = nomMois.substring(0, 1).toUpperCase(Locale.FRANCE) + nomMois.substring(1) + " " + mois.getYear();
            texte(g, nomMois, Font.BOLD, 36, Theme.TEXT, M, M + 64);

            titre(g, "TON HUMEUR DU MOIS", M, 142);
            if (bilan.dominante().isPresent()) {
                Sector s = bilan.dominante().get();
                texte(g, s.label(), Font.BOLD, 30, Mood.mix(c, Color.WHITE, 0.3), M, 178);
                texte(g, s.description(), Font.PLAIN, 14, new Color(0xD7DAE0), M, 202);
            } else {
                texte(g, "Pas encore d'humeur ce mois-ci", Font.BOLD, 20, Theme.TEXT, M, 176);
            }

            titre(g, "TON MOIS, JOUR PAR JOUR", M, 240);
            peindreCalendrier(g, M, 252);
        }

        /** Un calendrier : chaque jour prend la couleur de l'humeur moyenne de la journée. */
        private void peindreCalendrier(Graphics2D g, int x0, int y0) {
            final int cw = 44, ch = 30, gap = 6;
            String[] jours = {"L", "M", "M", "J", "V", "S", "D"};
            g.setFont(Theme.font(Font.BOLD, 10.5f));
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < 7; i++) {
                g.setColor(Theme.MUTED);
                g.drawString(jours[i], x0 + i * (cw + gap) + (cw - fm.stringWidth(jours[i])) / 2, y0 + 10);
            }
            int decalage = mois.atDay(1).getDayOfWeek().getValue() - 1; // lundi = 0
            LocalDate aujourdHui = LocalDate.now();
            g.setFont(Theme.font(Font.BOLD, 11.5f));
            fm = g.getFontMetrics();
            for (int jour = 1; jour <= mois.lengthOfMonth(); jour++) {
                int pos = decalage + jour - 1;
                int x = x0 + (pos % 7) * (cw + gap), y = y0 + 18 + (pos / 7) * (ch + gap);
                RoundRectangle2D r = new RoundRectangle2D.Double(x, y, cw, ch, 10, 10);
                Mood m = bilan.parJour().get(mois.atDay(jour));
                Color texte;
                if (m != null) {
                    g.setColor(m.color());
                    g.fill(r);
                    texte = Theme.readableOn(m.color());
                } else {
                    g.setColor(new Color(255, 255, 255, 14));
                    g.fill(r);
                    texte = new Color(255, 255, 255, 70);
                }
                if (mois.atDay(jour).equals(aujourdHui)) {
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(1.6f));
                    g.draw(r);
                }
                String n = String.valueOf(jour);
                g.setColor(texte);
                g.drawString(n, x + (cw - fm.stringWidth(n)) / 2f, y + (ch - fm.getHeight()) / 2f + fm.getAscent());
            }
        }

        // ---------------------------------------------------------------- colonne de droite

        private void peindreDroite(Graphics2D g, Color c) {
            int x = 470, largeur = W - M - x;

            // chiffres clés
            String[][] stats = {
                    {String.valueOf(bilan.nbMoments()), bilan.nbMoments() > 1 ? "humeurs notées" : "humeur notée"},
                    {String.valueOf(bilan.minutes()), "min d'écoute"},
                    {String.valueOf(bilan.nbEcoutes()), bilan.nbEcoutes() > 1 ? "morceaux" : "morceau"}};
            for (int i = 0; i < stats.length; i++) {
                int sx = x + i * largeur / 3;
                texte(g, stats[i][0], Font.BOLD, 32, Theme.TEXT, sx, M + 44);
                texte(g, stats[i][1], Font.PLAIN, 12, Theme.MUTED, sx, M + 64);
            }

            // ce qui t'aide vraiment
            titre(g, "CE QUI T'AIDE VRAIMENT", x, 142);
            List<Bilan.Effet> effets = bilan.effets();
            if (effets.isEmpty()) {
                paragraphe(g, "Pas encore assez de données. Après une station, un voyage ou une catégorie, "
                        + "dis à hmusic comment tu te sens (clic sur le pavé ou une phrase) : il saura ce qui te fait du bien.",
                        x, 166, largeur);
            } else {
                int y = 168;
                for (Bilan.Effet e : effets.subList(0, Math.min(3, effets.size()))) {
                    boolean aide = e.ameliorations() * 2 >= e.mesures() && e.gainMoyen() > 0;
                    g.setColor(aide ? VERT : Theme.MUTED);
                    g.fill(new Ellipse2D.Double(x, y - 9, 10, 10));
                    texte(g, Theme.fit(g.getFontMetrics(Theme.font(Font.BOLD, 15)), e.musique(), largeur - 20),
                            Font.BOLD, 15, Theme.TEXT, x + 18, y);
                    String detail = "humeur plus agréable " + e.ameliorations() + " fois sur " + e.mesures()
                            + String.format(Locale.FRANCE, "  ·  valence %+.2f", e.gainMoyen());
                    detail = Theme.fit(g.getFontMetrics(Theme.font(Font.PLAIN, 12)), detail, largeur - 18);
                    texte(g, detail, Font.PLAIN, 12, new Color(0xC5C9D0), x + 18, y + 18);
                    y += 44;
                }
            }

            // artistes
            titre(g, "TES ARTISTES DU MOIS", x, 312);
            if (bilan.topArtistes().isEmpty()) {
                texte(g, "Aucune écoute en mode Écouter ce mois-ci.", Font.PLAIN, 13, Theme.MUTED, x, 336);
            } else {
                int y = 338;
                int rang = 1;
                for (Bilan.Classement a : bilan.topArtistes()) {
                    texte(g, rang + ".", Font.BOLD, 15, Mood.mix(c, Color.WHITE, 0.3), x, y);
                    texte(g, Theme.fit(g.getFontMetrics(Theme.font(Font.BOLD, 15)), a.nom(), largeur - 120),
                            Font.BOLD, 15, Theme.TEXT, x + 24, y);
                    String n = a.nombre() + (a.nombre() > 1 ? " écoutes" : " écoute");
                    g.setFont(Theme.font(Font.PLAIN, 12.5f));
                    texte(g, n, Font.PLAIN, 12.5f, Theme.MUTED, x + largeur - g.getFontMetrics().stringWidth(n), y);
                    y += 24;
                    rang++;
                }
            }

            // musique fétiche + goûts appris
            titre(g, "TA MUSIQUE FÉTICHE", x, 426);
            texte(g, bilan.musiquePreferee().orElse("—"), Font.BOLD, 15, Theme.TEXT, x, 448);
            if (gouts != null) {
                titre(g, "CE QUE HMUSIC A APPRIS DE TES GOÛTS", x, 482);
                List<String> pref = gouts.preferes(5);
                if (pref.isEmpty()) {
                    texte(g, "Mets des morceaux en favoris : hmusic apprendra.", Font.PLAIN, 12.5f, Theme.MUTED, x, 504);
                } else {
                    int cx = x;
                    for (String p : pref) {
                        int w = pastille(g, p, cx, 490, c);
                        if (w < 0) break;
                        cx += w + 6;
                    }
                }
            }
        }

        // ---------------------------------------------------------------- petits outils de dessin

        private int pastille(Graphics2D g, String texte, int x, int y, Color c) {
            g.setFont(Theme.font(Font.BOLD, 11.5f));
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(texte) + 20, h = 24;
            if (x + w > W - M) return -1;
            g.setColor(Theme.alpha(Mood.mix(c, Color.WHITE, 0.2), 60));
            g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
            g.setColor(Theme.TEXT);
            g.drawString(texte, x + 10, y + (h - fm.getHeight()) / 2f + fm.getAscent());
            return w;
        }

        private static void titre(Graphics2D g, String t, int x, int y) {
            texte(g, t, Font.BOLD, 11, Theme.MUTED, x, y);
        }

        private static void texte(Graphics2D g, String t, int style, float taille, Color couleur, int x, int y) {
            g.setFont(Theme.font(style, taille));
            g.setColor(couleur);
            g.drawString(t, x, y);
        }

        /** Texte sur plusieurs lignes (retour à la ligne entre les mots). */
        private static void paragraphe(Graphics2D g, String t, int x, int y, int largeur) {
            g.setFont(Theme.font(Font.PLAIN, 13));
            g.setColor(new Color(0xC5C9D0));
            FontMetrics fm = g.getFontMetrics();
            List<String> lignes = new ArrayList<>();
            StringBuilder ligne = new StringBuilder();
            for (String mot : t.split(" ")) {
                String essai = ligne.isEmpty() ? mot : ligne + " " + mot;
                if (fm.stringWidth(essai) > largeur && !ligne.isEmpty()) {
                    lignes.add(ligne.toString());
                    ligne = new StringBuilder(mot);
                } else {
                    ligne = new StringBuilder(essai);
                }
            }
            lignes.add(ligne.toString());
            for (String l : lignes) {
                g.drawString(l, x, y);
                y += fm.getHeight() + 2;
            }
        }
    }
}
