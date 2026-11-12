package hmusic.utilisateur;

/**
 * Les actions qui peuvent apparaître dans le menu d'un utilisateur.
 * Chaque type d'utilisateur choisit lesquelles il propose (voir {@link Utilisateur#menu()}).
 */
public enum OptionMenu {
    // Auditeur
    MES_FAVORIS("Mes favoris"),
    MES_ABONNEMENTS("Mes abonnements"),
    // Admin
    AJOUTER_MORCEAU("Ajouter un morceau"),
    GERER_CATALOGUE("Gérer le catalogue"),
    IMPORTER_CATALOGUE("Importer depuis Jamendo"),
    // Communes à tous
    JOURNAL("Mon journal d'humeur"),
    DECONNEXION("Se déconnecter");

    private final String libelle;

    OptionMenu(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
