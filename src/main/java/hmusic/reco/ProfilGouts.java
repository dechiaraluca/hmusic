package hmusic.reco;

import hmusic.catalogue.Morceau;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

/**
 * Ce que hmusic a appris des goûts d'un auditeur, à partir de ce qu'il fait (pas de questionnaire).
 * <p>
 * Chaque signal ajoute ou retire des points aux <b>tags d'ambiance</b>, au <b>genre</b> et à
 * l'<b>artiste</b> du morceau concerné :
 * <table>
 *   <tr><td>mis en favori</td><td>{@value #FAVORI}</td></tr>
 *   <tr><td>artiste aimé</td><td>{@value #ARTISTE_AIME} (artiste seulement)</td></tr>
 *   <tr><td>écouté jusqu'au bout</td><td>{@value #ECOUTE_COMPLETE}</td></tr>
 *   <tr><td>passé avant 30 secondes</td><td>{@value #PASSE}</td></tr>
 * </table>
 * Les poids sont bornés entre {@value #MIN} et {@value #MAX} : un seul goût ne peut pas tout écraser,
 * et on peut toujours changer d'avis.
 */
public class ProfilGouts implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int FAVORI = 3, ARTISTE_AIME = 3, ECOUTE_COMPLETE = 1, PASSE = -1;
    static final int MIN = -6, MAX = 12;

    private final Map<String, Integer> tags = new HashMap<>();
    private final Map<String, Integer> genres = new HashMap<>();
    private final Map<String, Integer> artistes = new HashMap<>();

    /** Un signal sur un morceau (positif = aimé, négatif = pas aimé). */
    public void signaler(Morceau m, int poids) {
        for (String tag : m.tags()) ajuster(tags, cle(tag), poids);
        ajuster(genres, m.genre(), poids);
        ajuster(artistes, cle(m.artiste()), poids);
    }

    /** Un signal sur un artiste seulement (bouton « Aimer l'artiste »). */
    public void signalerArtiste(String artiste, int poids) {
        ajuster(artistes, cle(artiste), poids);
    }

    /**
     * À quel point ce morceau correspond aux goûts appris. 0 = on ne sait rien.
     * L'artiste compte double : c'est le signal le plus précis.
     */
    public int score(Morceau m) {
        int s = 0;
        for (String tag : m.tags()) s += tags.getOrDefault(cle(tag), 0);
        s += genres.getOrDefault(m.genre(), 0);
        s += 2 * artistes.getOrDefault(cle(m.artiste()), 0);
        return s;
    }

    public boolean estVide() {
        return tags.isEmpty() && genres.isEmpty() && artistes.isEmpty();
    }

    /** Les ambiances et genres préférés (poids positifs), du plus aimé au moins aimé. */
    public List<String> preferes(int max) {
        Map<String, Integer> tout = new HashMap<>(tags);
        genres.forEach((g, p) -> tout.merge(g, p, Math::max));
        return tout.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(max)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static void ajuster(Map<String, Integer> poids, String cle, int delta) {
        if (cle.isBlank()) return;
        int v = Math.max(MIN, Math.min(MAX, poids.getOrDefault(cle, 0) + delta));
        if (v == 0) poids.remove(cle); else poids.put(cle, v);
    }

    private static String cle(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
