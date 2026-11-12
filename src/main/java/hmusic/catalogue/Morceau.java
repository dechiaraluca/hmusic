package hmusic.catalogue;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Un morceau du catalogue.
 * <p>
 * Les champs obligatoires sont le titre, l'artiste et le genre. Les autres viennent de Jamendo
 * (lien audio, pochette…) et peuvent être vides pour un morceau ajouté à la main par un Admin.
 * <p>
 * C'est un {@code record} : un objet immuable dont Java génère automatiquement le constructeur,
 * les accesseurs, {@code equals}, {@code hashCode} et {@code toString}.
 */
public record Morceau(String id, String titre, String artiste, String genre, String album, int dureeSecondes,
                      String urlAudio, String urlImage, String urlJamendo, List<String> tags) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public Morceau {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Un morceau doit avoir un identifiant");
        if (titre == null || titre.isBlank()) throw new IllegalArgumentException("Le titre est obligatoire");
        if (artiste == null || artiste.isBlank()) throw new IllegalArgumentException("L'artiste est obligatoire");
        titre = titre.trim();
        artiste = artiste.trim();
        genre = genre == null || genre.isBlank() ? "divers" : genre.trim().toLowerCase();
        album = album == null ? "" : album.trim();
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** Peut-on l'écouter ? (un morceau ajouté à la main n'a pas forcément de fichier audio) */
    public boolean estJouable() {
        return urlAudio != null && !urlAudio.isBlank();
    }

    public String dureeTexte() {
        if (dureeSecondes <= 0) return "--:--";
        return String.format("%d:%02d", dureeSecondes / 60, dureeSecondes % 60);
    }

    @Override
    public String toString() {
        return titre + " — " + artiste + " (" + genre + ")";
    }
}
