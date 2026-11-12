package hmusic.reco;

import hmusic.catalogue.Catalogue;

/**
 * Propose une playlist à partir d'une humeur et du catalogue.
 * <p>
 * Pourquoi une interface ? Parce qu'on veut pouvoir changer de façon de recommander sans toucher au reste
 * du programme. L'interface graphique ne connaît que {@code Recommandeur} ; on lui donne au lancement
 * soit un {@link MockRecommandeur} (sans réseau, prévisible : idéal pour les tests et pour une démo
 * hors ligne), soit un {@code IaRecommandeur} (qui interroge une IA). Les deux n'ont aucun code en commun
 * à partager : une interface (un « contrat ») convient mieux qu'une classe abstraite.
 */
public interface Recommandeur {

    /**
     * @param humeur    ce que l'utilisateur a écrit (« fatigué mais content »…)
     * @param catalogue les morceaux autorisés
     * @param taille    nombre maximal de morceaux
     * @return une playlist dont tous les morceaux existent dans le catalogue
     */
    Playlist recommander(String humeur, Catalogue catalogue, int taille) throws RecommandationException;

    /** Nom affiché dans l'interface. */
    String nom();
}
