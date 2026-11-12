package hmusic.reco;

import hmusic.catalogue.Catalogue;
import hmusic.catalogue.Morceau;
import hmusic.ia.AiClient;
import hmusic.ia.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de IaRecommandeur sans réseau : on lui donne un faux AiClient qui renvoie une réponse
 * écrite à l'avance (c'est tout l'intérêt de dépendre de l'interface AiClient).
 */
class IaRecommandeurTest {

    /** Faux client : renvoie toujours la même réponse et garde la dernière question reçue. */
    private static final class FauxClient implements AiClient {
        private final String reponse;
        String derniereQuestion;
        int resets;

        FauxClient(String reponse) {
            this.reponse = reponse;
        }

        @Override public String ask(String question) {
            derniereQuestion = question;
            return reponse;
        }

        @Override public void reset() {
            resets++;
        }
    }

    private Catalogue catalogue;

    @BeforeEach
    void creerCatalogue() {
        catalogue = new Catalogue();
        for (int i = 1; i <= 5; i++) {
            catalogue.ajouter(new Morceau("jamendo-" + (100 + i), "Titre " + i, "Artiste " + i, i % 2 == 0 ? "jazz" : "rock",
                    "", 200, "https://exemple.org/" + i + ".mp3", null, null, List.of()));
        }
    }

    @Test
    void lesMorceauxInventesSontRejetesEtLesAutresGardes() throws Exception {
        FauxClient ia = new FauxClient("jamendo-101\njamendo-777\nImagine - John Lennon\njamendo-104");
        Playlist p = new IaRecommandeur(ia).recommander("heureux", catalogue, 10);
        assertEquals(List.of("jamendo-101", "jamendo-104"), p.morceaux().stream().map(Morceau::id).toList());
        assertEquals(List.of("jamendo-777", "Imagine - John Lennon"), p.rejetes());
        assertEquals("recommandeur IA", p.source());
    }

    @Test
    void lePromptEnvoyeContientLHumeurEtLeCatalogue() throws Exception {
        FauxClient ia = new FauxClient("jamendo-101");
        new IaRecommandeur(ia).recommander("nostalgique ce soir", catalogue, 3);
        assertTrue(ia.derniereQuestion.contains("nostalgique ce soir"));
        assertTrue(ia.derniereQuestion.contains("jamendo-105"));
        assertEquals(1, ia.resets, "chaque playlist repart d'une conversation vide");
    }

    @Test
    void siLIaNInventeQueDesMorceauxOnObtientUneException() {
        FauxClient ia = new FauxClient("jamendo-900\njamendo-901");
        RecommandationException e = assertThrows(RecommandationException.class,
                () -> new IaRecommandeur(ia).recommander("triste", catalogue, 5));
        assertTrue(e.getMessage().contains("2"), e.getMessage());
    }

    @Test
    void uneErreurDeLIaDevientUneRecommandationException() {
        AiClient enPanne = new AiClient() {
            @Override public String ask(String question) { throw new AiException("Clé API refusée (401)."); }
            @Override public void reset() { }
        };
        RecommandationException e = assertThrows(RecommandationException.class,
                () -> new IaRecommandeur(enPanne).recommander("calme", catalogue, 5));
        assertTrue(e.getMessage().contains("401"));
    }

    @Test
    void unGrosCatalogueEstEchantillonneEnGardantTousLesGenres() {
        Catalogue gros = new Catalogue();
        for (int i = 0; i < 400; i++) {
            gros.ajouter(new Morceau("jamendo-" + (1000 + i), "T" + i, "A" + i, "genre" + (i % 8), "", 200,
                    "https://exemple.org/" + i + ".mp3", null, null, List.of()));
        }
        Catalogue echantillon = IaRecommandeur.candidats(gros);
        assertEquals(IaRecommandeur.MAX_CANDIDATS, echantillon.taille());
        assertEquals(gros.genres(), echantillon.genres());
    }
}
