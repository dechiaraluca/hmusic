package hmusic.reco;

import hmusic.catalogue.Catalogue;
import hmusic.catalogue.Morceau;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ce qui entoure l'appel à l'IA, sans réseau (donc testable avec JUnit) :
 * <ul>
 *   <li>{@link #prompt} : construit la question envoyée à l'IA (humeur + liste du catalogue) ;</li>
 *   <li>{@link #controler} : <b>contrôle la réponse</b>. Tout morceau proposé qui n'existe pas dans
 *       le catalogue est rejeté. C'est ici que l'on se protège des « hallucinations » de l'IA.</li>
 * </ul>
 */
public final class PlaylistIa {

    /**
     * Un identifiant de morceau dans une ligne de réponse : « jamendo-1234 » (import), « local-1234 »
     * (ajout par un Admin), ou un nombre seul de 3 chiffres ou plus (l'IA oublie parfois le préfixe).
     */
    private static final Pattern ID = Pattern.compile("(?i)\\b(?:jamendo|local)-\\d+\\b|\\b\\d{3,}\\b");
    /** Une ligne « titre - artiste » (tiret, tiret long ou barre verticale). */
    private static final Pattern TITRE_ARTISTE = Pattern.compile("^(.+?)\\s+[-–—|]\\s+(.+)$");

    private PlaylistIa() {}

    public static String prompt(String humeur, Catalogue catalogue, int taille) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un programmateur musical. Compose une playlist de ").append(taille)
                .append(" morceaux maximum adaptée à l'humeur suivante : « ").append(humeur.trim()).append(" ».\n\n")
                .append("Règles strictes :\n")
                .append("- Choisis UNIQUEMENT parmi les morceaux du catalogue ci-dessous.\n")
                .append("- N'invente aucun morceau.\n")
                .append("- Réponds uniquement par les identifiants (première colonne), un par ligne, sans autre texte.\n")
                .append("- Ordonne la playlist pour qu'elle s'enchaîne bien.\n\n")
                .append("Catalogue (identifiant | titre | artiste | genre | ambiances) :\n");
        for (Morceau m : catalogue.tous()) {
            sb.append(m.id()).append(" | ").append(m.titre()).append(" | ").append(m.artiste())
                    .append(" | ").append(m.genre()).append(" | ")
                    .append(String.join(", ", m.tags().subList(0, Math.min(4, m.tags().size()))))
                    .append('\n');
        }
        return sb.toString();
    }

    /**
     * Garde seulement les morceaux de la réponse qui existent dans le catalogue.
     * <ul>
     *   <li>ligne avec un identifiant connu → morceau retenu ;</li>
     *   <li>ligne « titre - artiste » présente dans le catalogue → morceau retenu ;</li>
     *   <li>ligne avec un identifiant ou un « titre - artiste » inconnu → <b>rejetée</b> ;</li>
     *   <li>autre texte (phrase d'introduction…) → ignoré.</li>
     * </ul>
     */
    public static Playlist controler(String humeur, String reponse, Catalogue catalogue, int taille, String source) {
        Set<Morceau> retenus = new LinkedHashSet<>(); // LinkedHashSet : pas de doublon, ordre de l'IA conservé
        List<String> rejetes = new ArrayList<>();
        if (reponse == null) reponse = "";

        for (String brute : reponse.split("\\R")) {
            String ligne = brute.replaceAll("^[\\s*•>#-]*(\\d+[.)]\\s+)?", "").trim(); // retire puces et numéros
            if (ligne.isEmpty()) continue;

            Morceau trouve = parIdentifiant(ligne, catalogue);
            boolean ressembleAUnMorceau = ID.matcher(ligne).find();
            if (trouve == null) {
                Matcher ta = TITRE_ARTISTE.matcher(ligne);
                if (ta.matches()) {
                    ressembleAUnMorceau = true;
                    trouve = catalogue.trouver(ta.group(1).trim(), ta.group(2).trim()).orElse(null);
                }
            }
            if (trouve != null) {
                if (retenus.size() < taille) retenus.add(trouve);
            } else if (ressembleAUnMorceau) {
                rejetes.add(ligne); // l'IA a proposé quelque chose qui n'est pas dans le catalogue
            }
        }
        return new Playlist(humeur, new ArrayList<>(retenus), rejetes, source);
    }

    private static Morceau parIdentifiant(String ligne, Catalogue catalogue) {
        Matcher m = ID.matcher(ligne);
        while (m.find()) {
            String token = m.group().toLowerCase();
            if (catalogue.contient(token)) return catalogue.trouver(token).orElseThrow();
            if (catalogue.contient("jamendo-" + token)) return catalogue.trouver("jamendo-" + token).orElseThrow();
        }
        return null;
    }
}
