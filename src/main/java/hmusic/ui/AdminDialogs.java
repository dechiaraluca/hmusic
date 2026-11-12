package hmusic.ui;

import hmusic.catalogue.Catalogue;
import hmusic.catalogue.Morceau;
import hmusic.utilisateur.Admin;
import hmusic.utilisateur.Auditeur;
import hmusic.utilisateur.Utilisateur;

import javax.swing.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Fenêtres de l'administrateur : ajouter un morceau, gérer (consulter / supprimer) le catalogue. */
public final class AdminDialogs {

    private AdminDialogs() {}

    /**
     * Formulaire d'ajout d'un morceau à la main.
     *
     * @param modifie appelé après l'ajout (pour sauvegarder le catalogue)
     */
    public static void ajouterMorceau(Window owner, Catalogue catalogue, Runnable modifie) {
        HintField titre = new HintField("Titre *");
        HintField artiste = new HintField("Artiste *");
        HintField genre = new HintField("Genre * (ex. : rock, jazz, ambient…)");
        HintField audio = new HintField("Lien audio MP3 (facultatif)");
        JLabel erreur = Theme.label(" ", Font.PLAIN, 12.5f, Dialogues.ERREUR);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{titre, artiste, genre, audio}) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            form.add(c);
            form.add(Box.createVerticalStrut(10));
        }
        JLabel genres = Theme.label("<html><div style='width:400px'>Genres existants : "
                + (catalogue.genres().isEmpty() ? "aucun" : String.join(", ", catalogue.genres())) + "</div></html>",
                Font.PLAIN, 12, Theme.MUTED);
        genres.setAlignmentX(Component.LEFT_ALIGNMENT);
        erreur.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(genres);
        form.add(Box.createVerticalStrut(8));
        form.add(erreur);

        JDialog[] d = new JDialog[1];
        PillButton ajouter = new PillButton("Ajouter", true, () -> {
            String g = genre.getText().trim();
            if (g.isEmpty()) {
                erreur.setText("Le genre est obligatoire.");
                return;
            }
            String url = audio.getText().trim();
            if (!url.isEmpty() && !url.matches("(?i)https?://\\S+")) {
                erreur.setText("Le lien audio doit commencer par http:// ou https://");
                return;
            }
            try {
                // identifiant unique pour un morceau ajouté à la main : « local- » + l'heure en millisecondes
                Morceau m = new Morceau("local-" + System.currentTimeMillis(), titre.getText(), artiste.getText(),
                        g, "", 0, url.isEmpty() ? null : url, null, null, List.of());
                if (catalogue.trouver(m.titre(), m.artiste()).isPresent()) {
                    erreur.setText("Ce morceau est déjà dans le catalogue.");
                    return;
                }
                catalogue.ajouter(m);
                modifie.run();
                d[0].dispose();
            } catch (IllegalArgumentException ex) {
                erreur.setText(ex.getMessage()); // titre ou artiste manquant (vérifié par Morceau)
            }
        });
        d[0] = Dialogues.fenetre(owner, "Ajouter un morceau", "Le morceau rejoindra le catalogue utilisé pour les playlists.",
                form, new PillButton("Annuler", false, () -> d[0].dispose()), ajouter);
        d[0].setSize(500, 470);
        d[0].setLocationRelativeTo(owner);
        d[0].setVisible(true);
    }

    /** Liste des comptes inscrits (identifiant, rôle, date d'inscription, activité). */
    public static void listerUtilisateurs(Window owner, List<Utilisateur> comptes) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy 'à' HH:mm", Locale.FRANCE);
        Dialogues.Liste<Utilisateur> liste = new Dialogues.Liste<>(
                u -> u.identifiant() + "  ·  " + u.role(),
                u -> "Inscrit le " + fmt.format(u.inscription())
                        + (u instanceof Auditeur a ? "  ·  " + a.morceauxFavoris().size() + " favori(s), "
                        + a.abonnements().size() + " abonnement(s)" : ""),
                u -> u instanceof Admin ? new Color(0xFF7A59) : new Color(0x2EC4B6));
        liste.setElements(comptes);
        long admins = comptes.stream().filter(u -> u instanceof Admin).count();
        JDialog[] d = new JDialog[1];
        d[0] = Dialogues.fenetre(owner, "Utilisateurs inscrits",
                comptes.size() + " compte(s) : " + admins + " admin(s), " + (comptes.size() - admins) + " auditeur(s)",
                Dialogues.defilement(liste), new PillButton("Fermer", true, () -> d[0].dispose()));
        d[0].setSize(520, 480);
        d[0].setLocationRelativeTo(owner);
        d[0].setVisible(true);
    }

    /** Consultation du catalogue par genre, recherche et suppression. */
    public static void gererCatalogue(Window owner, Catalogue catalogue, Runnable modifie) {
        Dialogues.Liste<Morceau> liste = new Dialogues.Liste<>(Morceau::titre,
                m -> m.artiste() + " · " + m.genre() + (m.estJouable() ? "" : " · sans audio"),
                m -> AuditeurDialogs.couleur(m.genre()));
        JComboBox<String> genres = new JComboBox<>();
        HintField recherche = new HintField("Rechercher (titre, artiste)");
        JLabel compte = Theme.label(" ", Font.PLAIN, 12, Theme.MUTED);

        Runnable rafraichir = () -> {
            String g = (String) genres.getSelectedItem();
            List<Morceau> base = g == null || g.startsWith("Tous") ? catalogue.tous() : catalogue.duGenre(g);
            String q = recherche.getText().trim();
            List<Morceau> res = new ArrayList<>();
            List<Morceau> trouves = catalogue.rechercher(q);
            for (Morceau m : base) if (q.isEmpty() || trouves.contains(m)) res.add(m);
            liste.setElements(res);
            compte.setText(res.size() + " morceau(x) affiché(s) · " + catalogue.taille() + " au total · "
                    + catalogue.genres().size() + " genre(s)");
        };
        Runnable remplirGenres = () -> {
            Object choix = genres.getSelectedItem();
            genres.removeAllItems();
            genres.addItem("Tous les genres");
            catalogue.genres().forEach(genres::addItem);
            if (choix != null) genres.setSelectedItem(choix);
        };
        remplirGenres.run();
        genres.addActionListener(e -> rafraichir.run());
        recherche.addActionListener(e -> rafraichir.run());
        recherche.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { rafraichir.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { rafraichir.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { rafraichir.run(); }
        });
        rafraichir.run();

        JPanel filtres = new JPanel(new BorderLayout(10, 0));
        filtres.setOpaque(false);
        filtres.add(genres, BorderLayout.WEST);
        filtres.add(recherche, BorderLayout.CENTER);

        JDialog[] d = new JDialog[1];
        PillButton supprimer = new PillButton("Supprimer", false, () -> {
            Morceau m = liste.getSelectedValue();
            if (m == null) return;
            int ok = JOptionPane.showConfirmDialog(d[0], "Supprimer « " + m.titre() + " » de " + m.artiste() + " du catalogue ?",
                    "Supprimer un morceau", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
            catalogue.supprimer(m.id());
            modifie.run();
            remplirGenres.run();
            rafraichir.run();
        });
        PillButton ajouter = new PillButton("Ajouter…", false, () -> {
            ajouterMorceau(d[0], catalogue, modifie);
            remplirGenres.run();
            rafraichir.run();
        });

        JPanel centre = new JPanel(new BorderLayout(0, 10));
        centre.setOpaque(false);
        centre.add(filtres, BorderLayout.NORTH);
        centre.add(Dialogues.defilement(liste), BorderLayout.CENTER);
        centre.add(compte, BorderLayout.SOUTH);

        d[0] = Dialogues.fenetre(owner, "Catalogue", "Morceaux disponibles pour les playlists, rangés par genre.",
                centre, ajouter, supprimer, new PillButton("Fermer", true, () -> d[0].dispose()));
        d[0].setSize(640, 640);
        d[0].setLocationRelativeTo(owner);
        d[0].setVisible(true);
    }
}
