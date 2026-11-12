package hmusic.ui;

import hmusic.catalogue.Morceau;
import hmusic.model.Sector;
import hmusic.utilisateur.Auditeur;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Fenêtres de l'auditeur : « Mes favoris » et « Mes abonnements ». */
public final class AuditeurDialogs {

    private AuditeurDialogs() {}

    /** Couleur stable dérivée d'un texte (genre, nom d'artiste) pour les pastilles. */
    static Color couleur(String texte) {
        return Sector.RING[Math.floorMod(texte.hashCode(), Sector.RING.length)].color();
    }

    /**
     * @param jouer        lance une liste de morceaux (liste, nom de la playlist)
     * @param jouerArtiste lance les morceaux d'un artiste
     * @param modifie      appelé après chaque modification (pour sauvegarder)
     */
    public static void favoris(Window owner, Auditeur a, BiConsumer<List<Morceau>, String> jouer,
                               Consumer<String> jouerArtiste, Runnable modifie) {
        Dialogues.Liste<Morceau> morceaux = new Dialogues.Liste<>(Morceau::titre,
                m -> m.artiste() + " · " + m.genre() + " · " + m.dureeTexte(), m -> couleur(m.genre()));
        Dialogues.Liste<String> artistes = new Dialogues.Liste<>(s -> s, s -> "Artiste favori", AuditeurDialogs::couleur);
        Runnable rafraichir = () -> {
            morceaux.setElements(a.morceauxFavoris());
            artistes.setElements(a.artistesFavoris());
        };
        rafraichir.run();

        JDialog[] d = new JDialog[1];
        JPanel boutonsMorceaux = rangee(
                new PillButton("Tout écouter", true, () -> {
                    if (a.morceauxFavoris().isEmpty()) return;
                    jouer.accept(a.morceauxFavoris(), "Mes favoris");
                    d[0].dispose();
                }),
                new PillButton("Écouter", false, () -> {
                    Morceau m = morceaux.getSelectedValue();
                    if (m == null) return;
                    jouer.accept(List.of(m), m.titre());
                    d[0].dispose();
                }),
                new PillButton("Retirer", false, () -> {
                    Morceau m = morceaux.getSelectedValue();
                    if (m == null) return;
                    a.retirerFavori(m.id());
                    modifie.run();
                    rafraichir.run();
                }));
        JPanel boutonsArtistes = rangee(
                new PillButton("Écouter", false, () -> {
                    String s = artistes.getSelectedValue();
                    if (s == null) return;
                    jouerArtiste.accept(s);
                    d[0].dispose();
                }),
                new PillButton("Retirer", false, () -> {
                    String s = artistes.getSelectedValue();
                    if (s == null) return;
                    a.basculerArtisteFavori(s);
                    modifie.run();
                    rafraichir.run();
                }));

        JPanel centre = new JPanel(new GridLayout(2, 1, 0, 14));
        centre.setOpaque(false);
        centre.add(bloc("MORCEAUX FAVORIS", morceaux, boutonsMorceaux));
        centre.add(bloc("ARTISTES FAVORIS", artistes, boutonsArtistes));

        d[0] = Dialogues.fenetre(owner, "Mes favoris",
                a.morceauxFavoris().size() + " morceau(x) · " + a.artistesFavoris().size() + " artiste(s)",
                centre, new PillButton("Fermer", true, () -> d[0].dispose()));
        d[0].setSize(560, 640);
        d[0].setLocationRelativeTo(owner);
        d[0].setVisible(true);
    }

    public static void abonnements(Window owner, Auditeur a, Consumer<String> jouerArtiste, Runnable modifie) {
        Dialogues.Liste<String> liste = new Dialogues.Liste<>(s -> s,
                s -> "Abonné·e · ses derniers morceaux sur Jamendo", AuditeurDialogs::couleur);
        liste.setElements(a.abonnements());
        JDialog[] d = new JDialog[1];
        JPanel boutons = rangee(
                new PillButton("Écouter ses derniers morceaux", false, () -> {
                    String s = liste.getSelectedValue();
                    if (s == null) return;
                    jouerArtiste.accept(s);
                    d[0].dispose();
                }),
                new PillButton("Se désabonner", false, () -> {
                    String s = liste.getSelectedValue();
                    if (s == null) return;
                    a.basculerAbonnement(s);
                    modifie.run();
                    liste.setElements(a.abonnements());
                }));
        d[0] = Dialogues.fenetre(owner, "Mes abonnements",
                a.abonnements().isEmpty() ? "Abonne-toi à un artiste depuis le mode Écouter."
                        : a.abonnements().size() + " artiste(s) suivi(s)",
                bloc("ARTISTES SUIVIS", liste, boutons), new PillButton("Fermer", true, () -> d[0].dispose()));
        d[0].setSize(520, 480);
        d[0].setLocationRelativeTo(owner);
        d[0].setVisible(true);
    }

    static JPanel bloc(String titre, JComponent liste, JComponent boutons) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.add(Dialogues.section(titre), BorderLayout.NORTH);
        p.add(Dialogues.defilement(liste), BorderLayout.CENTER);
        p.add(boutons, BorderLayout.SOUTH);
        return p;
    }

    static JPanel rangee(JComponent... composants) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        for (JComponent c : composants) {
            p.add(c);
            p.add(Box.createHorizontalStrut(8));
        }
        return p;
    }
}
