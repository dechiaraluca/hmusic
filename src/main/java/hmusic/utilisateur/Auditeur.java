package hmusic.utilisateur;

import hmusic.catalogue.Morceau;

import java.io.Serial;
import java.util.*;

/**
 * Un auditeur : il écoute, met des morceaux et des artistes en favoris, et s'abonne à des artistes.
 * <ul>
 *   <li>Favoris morceaux : {@code LinkedHashMap} (identifiant → morceau) pour garder l'ordre d'ajout
 *       et retrouver/retirer un morceau directement par son identifiant.</li>
 *   <li>Artistes favoris et abonnements : {@code LinkedHashSet}, car un artiste n'y apparaît qu'une fois.</li>
 * </ul>
 */
public class Auditeur extends Utilisateur {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, Morceau> morceauxFavoris = new LinkedHashMap<>();
    private final Set<String> artistesFavoris = new LinkedHashSet<>();
    private final Set<String> abonnements = new LinkedHashSet<>();

    public Auditeur(String identifiant, String motDePasse) {
        super(identifiant, motDePasse);
    }

    @Override
    public String role() {
        return "Auditeur";
    }

    @Override
    protected List<OptionMenu> optionsSpecifiques() {
        return List.of(OptionMenu.MES_FAVORIS, OptionMenu.MES_ABONNEMENTS);
    }

    // ---------------------------------------------------------------- morceaux favoris

    public boolean estFavori(Morceau m) {
        return morceauxFavoris.containsKey(m.id());
    }

    /** Ajoute ou retire le morceau des favoris. @return true s'il est maintenant en favori */
    public boolean basculerFavori(Morceau m) {
        if (morceauxFavoris.remove(m.id()) != null) return false;
        morceauxFavoris.put(m.id(), m);
        return true;
    }

    public void retirerFavori(String idMorceau) {
        morceauxFavoris.remove(idMorceau);
    }

    public List<Morceau> morceauxFavoris() {
        return List.copyOf(morceauxFavoris.values());
    }

    // ---------------------------------------------------------------- artistes favoris

    public boolean estArtisteFavori(String artiste) {
        return artistesFavoris.contains(artiste);
    }

    public boolean basculerArtisteFavori(String artiste) {
        if (artistesFavoris.remove(artiste)) return false;
        artistesFavoris.add(artiste);
        return true;
    }

    public List<String> artistesFavoris() {
        return List.copyOf(artistesFavoris);
    }

    // ---------------------------------------------------------------- abonnements

    public boolean estAbonne(String artiste) {
        return abonnements.contains(artiste);
    }

    public boolean basculerAbonnement(String artiste) {
        if (abonnements.remove(artiste)) return false;
        abonnements.add(artiste);
        return true;
    }

    public List<String> abonnements() {
        return List.copyOf(abonnements);
    }
}
