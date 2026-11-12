package hmusic.stream;

import hmusic.catalogue.Morceau;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Télécharge depuis la bibliothèque Jamendo quelques morceaux populaires de chaque genre,
 * pour remplir le catalogue. Le genre demandé à Jamendo devient le genre du morceau.
 * <p>
 * Le téléchargement se fait en arrière-plan ; l'ajout dans le catalogue est fait ensuite par
 * l'interface, sur le thread Swing, pour ne jamais modifier le catalogue depuis deux threads à la fois.
 */
public final class ImportJamendo {

    public static final List<String> GENRES = List.of(
            "pop", "rock", "electronic", "hiphop", "jazz", "classical", "ambient",
            "folk", "lounge", "soundtrack", "metal", "reggae", "blues", "world");

    private ImportJamendo() {}

    /**
     * @param parGenre    nombre de morceaux demandés par genre
     * @param progression reçoit un message d'avancement (appelé depuis le thread d'import)
     */
    public static List<Morceau> telecharger(JamendoClient client, int parGenre, Consumer<String> progression)
            throws IOException, InterruptedException {
        List<Morceau> morceaux = new ArrayList<>();
        for (int i = 0; i < GENRES.size(); i++) {
            String genre = GENRES.get(i);
            progression.accept("Genre « " + genre + " » (" + (i + 1) + "/" + GENRES.size() + ")…");
            morceaux.addAll(client.morceauxDuGenre(genre, parGenre));
        }
        return morceaux;
    }
}
