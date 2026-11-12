package hmusic.reco;

import hmusic.catalogue.Morceau;

import java.util.List;

/**
 * Une playlist proposée par un {@link Recommandeur}.
 *
 * @param humeur   l'humeur saisie par l'utilisateur
 * @param morceaux les morceaux retenus (tous présents dans le catalogue)
 * @param rejetes  les propositions rejetées car absentes du catalogue (inventées par l'IA)
 * @param source   qui a fait la proposition (« IA », « hors ligne »…)
 */
public record Playlist(String humeur, List<Morceau> morceaux, List<String> rejetes, String source) {

    public Playlist {
        morceaux = List.copyOf(morceaux);
        rejetes = List.copyOf(rejetes);
    }
}
