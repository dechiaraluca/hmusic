package hmusic;

import hmusic.catalogue.Catalogue;
import hmusic.config.MiseAJour;
import hmusic.config.Settings;
import hmusic.engine.MoodEngine;
import hmusic.ia.HttpAiClient;
import hmusic.reco.IaRecommandeur;
import hmusic.reco.MockRecommandeur;
import hmusic.reco.Recommandeur;
import hmusic.stream.ListenController;
import hmusic.ui.ConnexionDialog;
import hmusic.ui.MainWindow;
import hmusic.ui.Theme;
import hmusic.utilisateur.GestionUtilisateurs;
import hmusic.utilisateur.Utilisateur;

import javax.swing.*;
import java.awt.Desktop;
import java.awt.Window;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Point d'entrée de hmusic. */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");

        // Le compositeur charge une banque de sons de 32 Mo : on le prépare en arrière-plan
        // pendant que l'utilisateur se connecte, au lieu de bloquer l'affichage.
        CompletableFuture<MoodEngine> moteur = CompletableFuture.supplyAsync(() -> {
            try {
                return new MoodEngine();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });

        // Recherche d'une nouvelle version sur GitHub, elle aussi en arrière-plan.
        CompletableFuture<Optional<MiseAJour.Nouveaute>> miseAJour = CompletableFuture.supplyAsync(MiseAJour::verifier);

        SwingUtilities.invokeLater(() -> {
            Theme.install();
            ListenController listen = new ListenController(new Settings());
            GestionUtilisateurs comptes = GestionUtilisateurs.charger(GestionUtilisateurs.FICHIER);
            Catalogue catalogue = Catalogue.charger(Catalogue.FICHIER);
            Recommandeur recommandeur = choisirRecommandeur();
            ouvrirSession(moteur, listen, comptes, catalogue, recommandeur);
            // une fois connecté (fenêtre principale ouverte), on prévient s'il y a mieux
            miseAJour.thenAccept(n -> n.ifPresent(v -> SwingUtilities.invokeLater(() -> proposerMiseAJour(v))));
        });
    }

    /** Petite fenêtre « nouvelle version disponible », avec un bouton qui ouvre la page de téléchargement. */
    private static void proposerMiseAJour(MiseAJour.Nouveaute n) {
        Window parent = Arrays.stream(Window.getWindows()).filter(w -> w instanceof MainWindow && w.isShowing())
                .findFirst().orElse(null);
        Object[] choix = {"Télécharger", "Plus tard"};
        int r = JOptionPane.showOptionDialog(parent,
                "La version " + n.version() + " de hmusic est disponible (tu as la " + MiseAJour.versionActuelle() + ").\n"
                        + "Télécharge le nouveau zip et remplace l'ancien dossier hmusic : tes comptes et réglages sont conservés.",
                "Mise à jour disponible", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, choix, choix[0]);
        if (r == 0) {
            try {
                Desktop.getDesktop().browse(URI.create(n.page()));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(parent, "Ouvre cette adresse dans ton navigateur :\n" + n.page(),
                        "hmusic", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /**
     * Choix de l'implémentation de Recommandeur : le reste du programme ne voit que l'interface.
     * La clé de l'IA est lue dans la variable d'environnement AI_API_KEY (comme au Projet 3) :
     * elle n'est jamais écrite dans le code ni dans un fichier du projet.
     */
    private static Recommandeur choisirRecommandeur() {
        String cle = System.getenv("AI_API_KEY");
        if (cle == null || cle.isBlank()) {
            System.out.println("Variable AI_API_KEY absente : playlists proposées par le MockRecommandeur (hors ligne).");
            return new MockRecommandeur();
        }
        return new IaRecommandeur(new HttpAiClient(cle.trim(), IaRecommandeur.MESSAGE_SYSTEME));
    }

    /** Connexion, puis fenêtre principale. À la déconnexion, on recommence. */
    private static void ouvrirSession(CompletableFuture<MoodEngine> moteur, ListenController listen,
                                      GestionUtilisateurs comptes, Catalogue catalogue, Recommandeur recommandeur) {
        Utilisateur u = ConnexionDialog.demander(comptes);
        if (u == null) {             // fenêtre de connexion fermée : on quitte
            listen.shutdown();
            moteur.thenAccept(MoodEngine::shutdown);
            System.exit(0);
            return;
        }
        MoodEngine engine;
        try {
            // en général le moteur est prêt depuis longtemps ; sinon on attend la fin du chargement
            engine = moteur.join();
        } catch (CompletionException e) {
            JOptionPane.showMessageDialog(null,
                    "Impossible d'ouvrir le synthétiseur MIDI de Java :\n" + e.getCause().getMessage(),
                    "hmusic", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        new MainWindow(engine, listen, u, comptes, catalogue, recommandeur,
                () -> SwingUtilities.invokeLater(() -> ouvrirSession(moteur, listen, comptes, catalogue, recommandeur)))
                .setVisible(true);
    }
}
