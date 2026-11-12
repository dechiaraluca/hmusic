package hmusic.utilisateur;

import hmusic.data.Stockage;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Inscription, connexion et sauvegarde des comptes (par sérialisation, dans ~/.hmusic/utilisateurs.ser).
 * <p>
 * Les comptes sont rangés dans une Map identifiant → utilisateur : vérifier qu'un identifiant est libre
 * ou retrouver un compte à la connexion se fait directement, sans parcourir une liste.
 * <p>
 * Le tout premier compte créé devient Admin : ainsi, aucun mot de passe d'administrateur n'est écrit
 * dans le code.
 */
public class GestionUtilisateurs implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final Path FICHIER = Stockage.DOSSIER.resolve("utilisateurs.ser");
    private static final int MDP_MIN = 6;

    private final Map<String, Utilisateur> comptes = new LinkedHashMap<>();
    private transient Path fichier; // transient : le chemin n'est pas sauvegardé avec les comptes

    /** Charge les comptes existants (ou une liste vide au premier lancement). */
    public static GestionUtilisateurs charger(Path fichier) {
        GestionUtilisateurs g = Stockage.charger(fichier, GestionUtilisateurs::new);
        g.fichier = fichier;
        return g;
    }

    /** Erreur d'inscription ou de connexion, avec un message à afficher tel quel. */
    public static class CompteException extends Exception {
        public CompteException(String message) {
            super(message);
        }
    }

    public boolean aucunCompte() {
        return comptes.isEmpty();
    }

    /**
     * Crée un compte. Le premier compte de l'application est un Admin, les suivants des Auditeurs.
     */
    public Utilisateur inscrire(String identifiant, String motDePasse, String confirmation) throws CompteException {
        String id = identifiant == null ? "" : identifiant.trim();
        if (!id.matches("[A-Za-z0-9_.-]{3,20}")) {
            throw new CompteException("L'identifiant doit faire 3 à 20 caractères (lettres, chiffres, . _ -).");
        }
        if (comptes.containsKey(cle(id))) throw new CompteException("Cet identifiant est déjà pris.");
        if (motDePasse == null || motDePasse.length() < MDP_MIN) {
            throw new CompteException("Le mot de passe doit faire au moins " + MDP_MIN + " caractères.");
        }
        if (!motDePasse.equals(confirmation)) throw new CompteException("Les deux mots de passe ne correspondent pas.");

        Utilisateur u = aucunCompte() ? new Admin(id, motDePasse) : new Auditeur(id, motDePasse);
        comptes.put(cle(id), u);
        sauvegarder();
        return u;
    }

    public Utilisateur connecter(String identifiant, String motDePasse) throws CompteException {
        Utilisateur u = identifiant == null ? null : comptes.get(cle(identifiant.trim()));
        // même message dans les deux cas : on ne révèle pas si l'identifiant existe
        if (u == null || !u.verifierMotDePasse(motDePasse)) {
            throw new CompteException("Identifiant ou mot de passe incorrect.");
        }
        return u;
    }

    /** À appeler après chaque modification d'un compte (favoris, abonnements…). */
    public void sauvegarder() {
        if (fichier == null) return;
        try {
            Stockage.sauvegarder(fichier, this);
        } catch (IOException e) {
            System.err.println("Impossible d'enregistrer les comptes : " + e.getMessage());
        }
    }

    /** Les identifiants ne tiennent pas compte des majuscules : « Lea » et « lea » sont le même compte. */
    private static String cle(String identifiant) {
        return identifiant.toLowerCase(Locale.ROOT);
    }
}
