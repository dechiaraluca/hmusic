package hmusic.reco;

import hmusic.catalogue.Catalogue;
import hmusic.catalogue.Morceau;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests du Recommandeur hors ligne et du contrôle de la réponse de l'IA (aucun appel réseau). */
class RecommandeurTest {

    private Catalogue catalogue;

    private static Morceau morceau(String id, String titre, String artiste, String genre, String... tags) {
        return new Morceau(id, titre, artiste, genre, "", 200, "https://exemple.org/" + id + ".mp3", null, null, List.of(tags));
    }

    @BeforeEach
    void creerCatalogue() {
        catalogue = new Catalogue();
        catalogue.ajouter(morceau("jamendo-101", "Lac sans vent", "Mira", "ambient", "relaxing", "calm", "peaceful"));
        catalogue.ajouter(morceau("jamendo-102", "Thé vert", "Hoshi", "lounge", "chill", "relaxing"));
        catalogue.ajouter(morceau("jamendo-103", "Nocturne", "Elias", "classical", "calm", "piano"));
        catalogue.ajouter(morceau("jamendo-201", "Feu sacré", "Rage Unit", "metal", "aggressive", "dark", "intense"));
        catalogue.ajouter(morceau("jamendo-202", "Sirènes", "Blacktide", "rock", "dark", "energetic"));
        catalogue.ajouter(morceau("jamendo-301", "Confettis", "Sunny Days", "pop", "happy", "party", "dance"));
        catalogue.ajouter(morceau("jamendo-302", "Grand huit", "Neon Kids", "electronic", "energetic", "party"));
        catalogue.ajouter(morceau("local-1", "Parapluie", "Lou", "folk", "sad", "melancholic"));
    }

    // ------------------------------------------------------------------ MockRecommandeur

    @Test
    void leMockNeProposeQueDesMorceauxDuCatalogue() throws Exception {
        Playlist p = new MockRecommandeur().recommander("je suis heureux", catalogue, 5);
        assertFalse(p.morceaux().isEmpty());
        for (Morceau m : p.morceaux()) assertTrue(catalogue.contient(m.id()), m + " n'est pas dans le catalogue");
        assertTrue(p.rejetes().isEmpty());
    }

    @Test
    void leMockRespecteLaTailleDemandee() throws Exception {
        assertEquals(3, new MockRecommandeur().recommander("motivé", catalogue, 3).morceaux().size());
        // on ne peut pas proposer plus de morceaux que le catalogue n'en contient
        assertEquals(catalogue.taille(), new MockRecommandeur().recommander("motivé", catalogue, 50).morceaux().size());
    }

    @Test
    void uneHumeurCalmeDonneDesMorceauxCalmes() throws Exception {
        Playlist p = new MockRecommandeur().recommander("je suis calme et détendu", catalogue, 3);
        for (Morceau m : p.morceaux()) {
            assertTrue(List.of("ambient", "lounge", "classical").contains(m.genre()), "morceau pas calme : " + m);
        }
    }

    @Test
    void uneHumeurEnColereDonneDesMorceauxIntenses() throws Exception {
        Morceau premier = new MockRecommandeur().recommander("en colère et énervé", catalogue, 1).morceaux().get(0);
        assertTrue(List.of("metal", "rock").contains(premier.genre()), "premier morceau : " + premier);
    }

    @Test
    void leMockEstDeterministe() throws Exception {
        MockRecommandeur r = new MockRecommandeur();
        assertEquals(r.recommander("nostalgique", catalogue, 4).morceaux(),
                r.recommander("nostalgique", catalogue, 4).morceaux());
    }

    @Test
    void unCatalogueVideLeveUneException() {
        assertThrows(RecommandationException.class,
                () -> new MockRecommandeur().recommander("heureux", new Catalogue(), 5));
    }

    // ------------------------------------------------------------------ contrôle de la réponse de l'IA

    @Test
    void lesMorceauxInventesParLIaSontRejetes() {
        String reponseIa = """
                jamendo-101
                jamendo-999
                Bohemian Rhapsody - Queen
                jamendo-103
                """;
        Playlist p = PlaylistIa.controler("calme", reponseIa, catalogue, 10, "IA");
        assertEquals(List.of("jamendo-101", "jamendo-103"), p.morceaux().stream().map(Morceau::id).toList());
        assertEquals(2, p.rejetes().size());
        assertTrue(p.rejetes().contains("jamendo-999"));
        assertTrue(p.rejetes().contains("Bohemian Rhapsody - Queen"));
    }

    @Test
    void leControleAccepteTitreEtArtisteSiLeMorceauExiste() {
        Playlist p = PlaylistIa.controler("triste", "1. Parapluie — Lou\n2. thé VERT - hoshi", catalogue, 10, "IA");
        assertEquals(List.of("local-1", "jamendo-102"), p.morceaux().stream().map(Morceau::id).toList());
        assertTrue(p.rejetes().isEmpty());
    }

    @Test
    void leControleAccepteUnNumeroSansPrefixe() {
        Playlist p = PlaylistIa.controler("joie", "- 301\n- 302", catalogue, 10, "IA");
        assertEquals(List.of("jamendo-301", "jamendo-302"), p.morceaux().stream().map(Morceau::id).toList());
    }

    @Test
    void leControleIgnoreLesPhrasesEtLesDoublons() {
        String reponseIa = "Voici une playlist feel-good pour toi :\njamendo-301\njamendo-301\njamendo-302\nBonne écoute !";
        Playlist p = PlaylistIa.controler("joie", reponseIa, catalogue, 10, "IA");
        assertEquals(2, p.morceaux().size());
        assertTrue(p.rejetes().isEmpty(), "les phrases ne sont pas des morceaux inventés : " + p.rejetes());
    }

    @Test
    void leControleLimiteLaTaille() {
        Playlist p = PlaylistIa.controler("x", "jamendo-101\njamendo-102\njamendo-103", catalogue, 2, "IA");
        assertEquals(2, p.morceaux().size());
    }

    @Test
    void uneReponseVideDonneUnePlaylistVide() {
        Playlist p = PlaylistIa.controler("x", null, catalogue, 5, "IA");
        assertTrue(p.morceaux().isEmpty());
        assertTrue(p.rejetes().isEmpty());
    }

    @Test
    void lePromptContientLHumeurEtToutLeCatalogue() {
        String prompt = PlaylistIa.prompt("fatigué mais content", catalogue, 5);
        assertTrue(prompt.contains("fatigué mais content"));
        for (Morceau m : catalogue.tous()) assertTrue(prompt.contains(m.id()), "manque " + m.id());
    }
}
