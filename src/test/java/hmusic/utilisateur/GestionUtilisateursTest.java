package hmusic.utilisateur;

import hmusic.utilisateur.GestionUtilisateurs.CompteException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Inscription, connexion, mots de passe hachés, menus polymorphes et sauvegarde. */
class GestionUtilisateursTest {

    @TempDir
    Path dossier;

    private GestionUtilisateurs nouvelle() {
        return GestionUtilisateurs.charger(dossier.resolve("utilisateurs.ser"));
    }

    @Test
    void lePremierCompteEstAdminLesSuivantsAuditeurs() throws Exception {
        GestionUtilisateurs g = nouvelle();
        assertInstanceOf(Admin.class, g.inscrire("prof", "secret123", "secret123"));
        assertInstanceOf(Auditeur.class, g.inscrire("lea", "motdepasse", "motdepasse"));
    }

    @Test
    void leMenuDependDuTypeDUtilisateur() throws Exception {
        GestionUtilisateurs g = nouvelle();
        Utilisateur admin = g.inscrire("prof", "secret123", "secret123");
        Utilisateur auditeur = g.inscrire("lea", "motdepasse", "motdepasse");
        assertTrue(admin.menu().contains(OptionMenu.AJOUTER_MORCEAU));
        assertFalse(admin.menu().contains(OptionMenu.MES_FAVORIS));
        assertTrue(auditeur.menu().contains(OptionMenu.MES_FAVORIS));
        assertFalse(auditeur.menu().contains(OptionMenu.AJOUTER_MORCEAU));
        // options communes, ajoutées par la classe abstraite
        assertTrue(admin.menu().contains(OptionMenu.DECONNEXION));
        assertTrue(auditeur.menu().contains(OptionMenu.DECONNEXION));
    }

    @Test
    void connexionAvecLeBonEtLeMauvaisMotDePasse() throws Exception {
        GestionUtilisateurs g = nouvelle();
        g.inscrire("lea", "motdepasse", "motdepasse");
        assertEquals("lea", g.connecter("LEA", "motdepasse").identifiant()); // majuscules ignorées
        assertThrows(CompteException.class, () -> g.connecter("lea", "mauvais"));
        assertThrows(CompteException.class, () -> g.connecter("inconnu", "motdepasse"));
    }

    @Test
    void lesSaisiesInvalidesSontRefusees() throws Exception {
        GestionUtilisateurs g = nouvelle();
        assertThrows(CompteException.class, () -> g.inscrire("a", "motdepasse", "motdepasse"));      // trop court
        assertThrows(CompteException.class, () -> g.inscrire("lé a", "motdepasse", "motdepasse"));   // caractères interdits
        assertThrows(CompteException.class, () -> g.inscrire("lea", "123", "123"));                  // mot de passe trop court
        assertThrows(CompteException.class, () -> g.inscrire("lea", "motdepasse", "autre"));         // confirmation différente
        g.inscrire("lea", "motdepasse", "motdepasse");
        assertThrows(CompteException.class, () -> g.inscrire("Lea", "motdepasse", "motdepasse"));    // déjà pris
    }

    @Test
    void leMotDePasseEstHacheAvecUnSel() {
        String sel1 = MotDePasse.nouveauSel(), sel2 = MotDePasse.nouveauSel();
        assertNotEquals(sel1, sel2);
        String h1 = MotDePasse.hacher("motdepasse", sel1);
        assertNotEquals("motdepasse", h1);
        assertNotEquals(h1, MotDePasse.hacher("motdepasse", sel2), "même mot de passe, sels différents => empreintes différentes");
        assertTrue(MotDePasse.verifier("motdepasse", sel1, h1));
        assertFalse(MotDePasse.verifier("motdepass", sel1, h1));
    }

    @Test
    void lAdminPeutListerLesUtilisateursInscrits() throws Exception {
        GestionUtilisateurs g = nouvelle();
        Utilisateur admin = g.inscrire("prof", "secret123", "secret123");
        g.inscrire("lea", "motdepasse", "motdepasse");
        g.inscrire("tom", "motdepasse", "motdepasse");
        assertTrue(admin.menu().contains(OptionMenu.LISTER_UTILISATEURS));
        assertEquals(java.util.List.of("prof", "lea", "tom"),
                g.utilisateurs().stream().map(Utilisateur::identifiant).toList()); // ordre d'inscription
        assertThrows(UnsupportedOperationException.class, () -> g.utilisateurs().clear()); // copie non modifiable
    }

    @Test
    void lesComptesEtLesFavorisSontSauvegardes() throws Exception {
        GestionUtilisateurs g = nouvelle();
        g.inscrire("prof", "secret123", "secret123");
        Auditeur lea = (Auditeur) g.inscrire("lea", "motdepasse", "motdepasse");
        lea.basculerAbonnement("Mira");
        lea.basculerArtisteFavori("Hoshi");
        g.sauvegarder();

        GestionUtilisateurs relu = nouvelle(); // relit le fichier sérialisé
        Auditeur leaRelue = (Auditeur) relu.connecter("lea", "motdepasse");
        assertTrue(leaRelue.estAbonne("Mira"));
        assertTrue(leaRelue.estArtisteFavori("Hoshi"));
        assertInstanceOf(Admin.class, relu.connecter("prof", "secret123"));
    }
}
