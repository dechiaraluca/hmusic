package hmusic.reco;

import hmusic.catalogue.Catalogue;
import hmusic.catalogue.Morceau;
import hmusic.ia.AiClient;
import hmusic.ia.AiException;

import java.util.*;

/**
 * Recommandeur qui demande la playlist à une IA, via l'{@link AiClient} du Projet 3.
 * <ol>
 *   <li>on construit le prompt : l'humeur + la liste des morceaux autorisés ({@link PlaylistIa#prompt}) ;</li>
 *   <li>on interroge l'IA ;</li>
 *   <li>on <b>contrôle</b> sa réponse : tout morceau qui n'est pas dans le catalogue est rejeté
 *       ({@link PlaylistIa#controler}).</li>
 * </ol>
 */
public class IaRecommandeur implements Recommandeur {

    /** Message système envoyé à l'IA (remplace celui du « tuteur Java » du Projet 3). */
    public static final String MESSAGE_SYSTEME =
            "Tu es un programmateur musical. Tu composes des playlists uniquement à partir du catalogue fourni "
            + "et tu réponds seulement par des identifiants de morceaux, un par ligne.";

    /**
     * Nombre maximal de morceaux envoyés à l'IA. L'offre gratuite de Groq limite la taille des requêtes :
     * si le catalogue est plus grand, on envoie un échantillon qui contient tous les genres.
     */
    static final int MAX_CANDIDATS = 150;

    private final AiClient ia;

    public IaRecommandeur(AiClient ia) {
        this.ia = ia;
    }

    @Override
    public Playlist recommander(String humeur, Catalogue catalogue, int taille) throws RecommandationException {
        if (catalogue.estVide()) throw new RecommandationException("Le catalogue est vide.");
        if (humeur == null || humeur.isBlank()) throw new RecommandationException("Décris ton humeur en quelques mots.");

        String reponse;
        try {
            ia.reset(); // chaque playlist est une nouvelle demande, sans mémoire des précédentes
            reponse = ia.ask(PlaylistIa.prompt(humeur, candidats(catalogue), taille));
        } catch (AiException e) {
            throw new RecommandationException("IA indisponible : " + e.getMessage(), e);
        }

        // Contrôle avec le catalogue COMPLET : un identifiant inventé est rejeté ici
        Playlist p = PlaylistIa.controler(humeur, reponse, catalogue, taille, nom());
        if (p.morceaux().isEmpty()) {
            throw new RecommandationException("l'IA n'a proposé aucun morceau du catalogue"
                    + (p.rejetes().isEmpty() ? "." : " (" + p.rejetes().size() + " proposition(s) inventée(s) rejetée(s))."));
        }
        return p;
    }

    /** Au plus {@link #MAX_CANDIDATS} morceaux, pris tour à tour dans chaque genre. */
    static Catalogue candidats(Catalogue catalogue) {
        if (catalogue.taille() <= MAX_CANDIDATS) return catalogue;
        Catalogue echantillon = new Catalogue();
        List<Iterator<Morceau>> parGenre = new ArrayList<>();
        for (String g : catalogue.genres()) parGenre.add(catalogue.duGenre(g).iterator());
        boolean ajout = true;
        while (echantillon.taille() < MAX_CANDIDATS && ajout) {
            ajout = false;
            for (Iterator<Morceau> it : parGenre) {
                if (it.hasNext() && echantillon.taille() < MAX_CANDIDATS) {
                    echantillon.ajouter(it.next());
                    ajout = true;
                }
            }
        }
        return echantillon;
    }

    @Override
    public String nom() {
        return "recommandeur IA";
    }
}
