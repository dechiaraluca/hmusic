package hmusic.reco;

import hmusic.catalogue.Catalogue;
import hmusic.catalogue.Morceau;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Le recommandeur qui apprend : goûts appris + patron Décorateur. */
class RecommandeurPersonnaliseTest {

    private Catalogue catalogue;

    private static Morceau morceau(String id, String titre, String artiste, String genre, String... tags) {
        return new Morceau(id, titre, artiste, genre, "", 200, "https://exemple.org/" + id + ".mp3", null, null, List.of(tags));
    }

    @BeforeEach
    void creerCatalogue() {
        catalogue = new Catalogue();
        catalogue.ajouter(morceau("jamendo-101", "Lac sans vent", "Mira", "ambient", "relaxing", "calm"));
        catalogue.ajouter(morceau("jamendo-102", "Thé vert", "Hoshi", "lounge", "chill", "relaxing"));
        catalogue.ajouter(morceau("jamendo-103", "Nocturne", "Elias", "classical", "calm", "piano"));
        catalogue.ajouter(morceau("jamendo-104", "Ondes", "Mira", "ambient", "calm", "peaceful"));
        catalogue.ajouter(morceau("jamendo-105", "Jazz du soir", "Trio Lune", "jazz", "chill", "piano"));
        catalogue.ajouter(morceau("jamendo-201", "Feu sacré", "Rage Unit", "metal", "aggressive", "dark"));
    }

    private static List<String> ids(Playlist p) {
        return p.morceaux().stream().map(Morceau::id).toList();
    }

    @Test
    void sansGoutsAppris_laPlaylistEstCelleDuRecommandeurDeBase() throws Exception {
        Playlist base = new MockRecommandeur().recommander("je suis calme", catalogue, 3);
        Playlist perso = new RecommandeurPersonnalise(new MockRecommandeur(), new ProfilGouts())
                .recommander("je suis calme", catalogue, 3);
        assertEquals(ids(base), ids(perso));
    }

    @Test
    void unArtisteAimeRemonteDansLaPlaylist() throws Exception {
        ProfilGouts gouts = new ProfilGouts();
        gouts.signalerArtiste("Trio Lune", ProfilGouts.ARTISTE_AIME);
        gouts.signaler(catalogue.trouver("jamendo-105").orElseThrow(), ProfilGouts.FAVORI);

        List<String> base = ids(new MockRecommandeur().recommander("je suis calme", catalogue, 6));
        List<String> perso = ids(new RecommandeurPersonnalise(new MockRecommandeur(), gouts)
                .recommander("je suis calme", catalogue, 6));
        assertTrue(perso.indexOf("jamendo-105") < base.indexOf("jamendo-105"),
                "le morceau de l'artiste aimé doit être mieux classé : " + base + " -> " + perso);
    }

    @Test
    void unMorceauPasseDescend() {
        Morceau a = catalogue.trouver("jamendo-101").orElseThrow();
        Morceau b = catalogue.trouver("jamendo-102").orElseThrow();
        ProfilGouts gouts = new ProfilGouts();
        for (int i = 0; i < 3; i++) gouts.signaler(a, ProfilGouts.PASSE);  // passé 3 fois
        assertEquals(List.of(b, a), RecommandeurPersonnalise.reordonner(List.of(a, b), gouts));
    }

    @Test
    void leDecorateurNeProposeQueDesMorceauxDuCatalogue() throws Exception {
        ProfilGouts gouts = new ProfilGouts();
        gouts.signaler(catalogue.trouver("jamendo-201").orElseThrow(), ProfilGouts.FAVORI);
        Playlist p = new RecommandeurPersonnalise(new MockRecommandeur(), gouts).recommander("je suis calme", catalogue, 4);
        assertEquals(4, p.morceaux().size());
        for (Morceau m : p.morceaux()) assertTrue(catalogue.contient(m.id()));
        assertTrue(p.source().contains("goûts"));
    }

    @Test
    void lesPoidsSontBornes() {
        Morceau m = catalogue.trouver("jamendo-101").orElseThrow();
        ProfilGouts gouts = new ProfilGouts();
        for (int i = 0; i < 50; i++) gouts.signaler(m, ProfilGouts.FAVORI);
        // 2 tags + genre + 2 x artiste, chacun plafonné à MAX
        assertEquals(5 * ProfilGouts.MAX, gouts.score(m));
    }

    @Test
    void retirerUnFavoriAnnuleLeSignal() {
        Morceau m = catalogue.trouver("jamendo-101").orElseThrow();
        ProfilGouts gouts = new ProfilGouts();
        gouts.signaler(m, ProfilGouts.FAVORI);
        gouts.signaler(m, -ProfilGouts.FAVORI);
        assertTrue(gouts.estVide());
    }

    @Test
    void lesGoutsPreferesSontLesPlusAimes() {
        ProfilGouts gouts = new ProfilGouts();
        gouts.signaler(catalogue.trouver("jamendo-105").orElseThrow(), ProfilGouts.FAVORI); // jazz, chill, piano
        gouts.signaler(catalogue.trouver("jamendo-103").orElseThrow(), ProfilGouts.FAVORI); // classical, calm, piano
        assertEquals("piano", gouts.preferes(3).get(0));
    }
}
