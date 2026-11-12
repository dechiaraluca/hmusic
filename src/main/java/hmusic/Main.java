package hmusic;

import hmusic.catalogue.Catalogue;
import hmusic.config.Settings;
import hmusic.engine.MoodEngine;
import hmusic.reco.MockRecommandeur;
import hmusic.reco.Recommandeur;
import hmusic.stream.ListenController;
import hmusic.ui.ConnexionDialog;
import hmusic.ui.MainWindow;
import hmusic.ui.Theme;
import hmusic.utilisateur.GestionUtilisateurs;
import hmusic.utilisateur.Utilisateur;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.*;

/** Point d'entrée de hmusic. */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            MoodEngine engine;
            try {
                engine = new MoodEngine();
            } catch (MidiUnavailableException e) {
                JOptionPane.showMessageDialog(null,
                        "Impossible d'ouvrir le synthétiseur MIDI de Java :\n" + e.getMessage(),
                        "hmusic", JOptionPane.ERROR_MESSAGE);
                return;
            }
            ListenController listen = new ListenController(new Settings());
            GestionUtilisateurs comptes = GestionUtilisateurs.charger(GestionUtilisateurs.FICHIER);
            Catalogue catalogue = Catalogue.charger(Catalogue.FICHIER);
            Recommandeur recommandeur = choisirRecommandeur();
            ouvrirSession(engine, listen, comptes, catalogue, recommandeur);
        });
    }

    /** Choix de l'implémentation de Recommandeur : le reste du programme ne voit que l'interface. */
    private static Recommandeur choisirRecommandeur() {
        return new MockRecommandeur(); // TODO : IaRecommandeur dès que l'AiClient du Projet 3 est intégré
    }

    /** Connexion, puis fenêtre principale. À la déconnexion, on recommence. */
    private static void ouvrirSession(MoodEngine engine, ListenController listen, GestionUtilisateurs comptes,
                                      Catalogue catalogue, Recommandeur recommandeur) {
        Utilisateur u = ConnexionDialog.demander(comptes);
        if (u == null) {             // fenêtre de connexion fermée : on quitte
            listen.shutdown();
            engine.shutdown();
            System.exit(0);
            return;
        }
        new MainWindow(engine, listen, u, comptes, catalogue, recommandeur,
                () -> SwingUtilities.invokeLater(() -> ouvrirSession(engine, listen, comptes, catalogue, recommandeur)))
                .setVisible(true);
    }
}
