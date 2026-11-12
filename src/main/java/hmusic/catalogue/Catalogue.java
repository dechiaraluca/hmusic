package hmusic.catalogue;

import java.io.Serial;
import java.io.Serializable;
import java.text.Normalizer;
import java.util.*;

/**
 * Le catalogue de morceaux, rangé par genre.
 * <p>
 * Pourquoi une {@code Map} plutôt qu'une simple liste ?
 * <ul>
 *   <li>{@code parGenre} (genre → morceaux) : on obtient directement tous les morceaux d'un genre,
 *       sans parcourir tout le catalogue, et la liste des genres disponibles est simplement {@code keySet()}.</li>
 *   <li>{@code parId} (identifiant → morceau) : vérifier qu'un morceau existe se fait en temps constant.
 *       C'est ce qu'on utilise pour rejeter les morceaux inventés par l'IA.</li>
 * </ul>
 * Les deux Map sont toujours modifiées ensemble par {@link #ajouter} et {@link #supprimer}.
 */
public class Catalogue implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, List<Morceau>> parGenre = new TreeMap<>(); // TreeMap : genres triés par ordre alphabétique
    private final Map<String, Morceau> parId = new HashMap<>();

    /** @return false si un morceau avec le même identifiant existe déjà */
    public boolean ajouter(Morceau m) {
        Objects.requireNonNull(m);
        if (parId.containsKey(m.id())) return false;
        parId.put(m.id(), m);
        parGenre.computeIfAbsent(m.genre(), g -> new ArrayList<>()).add(m);
        return true;
    }

    /** @return le morceau supprimé, ou null s'il n'existait pas */
    public Morceau supprimer(String id) {
        Morceau m = parId.remove(id);
        if (m == null) return null;
        List<Morceau> liste = parGenre.get(m.genre());
        liste.remove(m);
        if (liste.isEmpty()) parGenre.remove(m.genre()); // pas de genre vide
        return m;
    }

    public boolean contient(String id) {
        return parId.containsKey(id);
    }

    public Optional<Morceau> trouver(String id) {
        return Optional.ofNullable(parId.get(id));
    }

    /** Recherche exacte (sans tenir compte des accents ni des majuscules) par titre et artiste. */
    public Optional<Morceau> trouver(String titre, String artiste) {
        String t = normaliser(titre), a = normaliser(artiste);
        for (Morceau m : parId.values()) {
            if (normaliser(m.titre()).equals(t) && normaliser(m.artiste()).equals(a)) return Optional.of(m);
        }
        return Optional.empty();
    }

    /** Morceaux dont le titre, l'artiste ou le genre contient le texte cherché. */
    public List<Morceau> rechercher(String texte) {
        String q = normaliser(texte);
        List<Morceau> res = new ArrayList<>();
        for (Morceau m : tous()) {
            if (q.isEmpty() || normaliser(m.titre()).contains(q) || normaliser(m.artiste()).contains(q)
                    || m.genre().contains(q)) {
                res.add(m);
            }
        }
        return res;
    }

    public Set<String> genres() {
        return Collections.unmodifiableSet(parGenre.keySet());
    }

    public List<Morceau> duGenre(String genre) {
        return Collections.unmodifiableList(parGenre.getOrDefault(genre, List.of()));
    }

    /** Tous les morceaux, genre par genre. */
    public List<Morceau> tous() {
        List<Morceau> res = new ArrayList<>();
        for (List<Morceau> l : parGenre.values()) res.addAll(l);
        return res;
    }

    public int taille() {
        return parId.size();
    }

    public boolean estVide() {
        return parId.isEmpty();
    }

    private static String normaliser(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
