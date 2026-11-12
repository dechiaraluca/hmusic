package hmusic.catalogue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogueTest {

    private static Morceau m(String id, String titre, String genre) {
        return new Morceau(id, titre, "Artiste", genre, "", 180, null, null, null, List.of());
    }

    @Test
    void lesMorceauxSontRangesParGenre() {
        Catalogue c = new Catalogue();
        c.ajouter(m("1", "A", "Rock"));
        c.ajouter(m("2", "B", "rock"));
        c.ajouter(m("3", "C", "jazz"));
        assertEquals(List.of("jazz", "rock"), List.copyOf(c.genres())); // triés, en minuscules
        assertEquals(2, c.duGenre("rock").size());
        assertEquals(3, c.taille());
    }

    @Test
    void unDoublonDIdentifiantEstRefuse() {
        Catalogue c = new Catalogue();
        assertTrue(c.ajouter(m("1", "A", "rock")));
        assertFalse(c.ajouter(m("1", "Autre", "jazz")));
        assertEquals(1, c.taille());
    }

    @Test
    void supprimerRetireDesDeuxMapEtEffaceLeGenreVide() {
        Catalogue c = new Catalogue();
        c.ajouter(m("1", "A", "rock"));
        c.ajouter(m("2", "B", "jazz"));
        assertNotNull(c.supprimer("1"));
        assertFalse(c.contient("1"));
        assertFalse(c.genres().contains("rock"));
        assertNull(c.supprimer("inconnu"));
    }

    @Test
    void unMorceauSansTitreEstInvalide() {
        assertThrows(IllegalArgumentException.class, () -> m("1", " ", "rock"));
    }
}
