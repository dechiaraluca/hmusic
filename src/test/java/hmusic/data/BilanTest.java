package hmusic.data;

import hmusic.model.Mood;
import hmusic.model.Sector;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Le bilan du mois : humeur dominante, calendrier, écoute et « ce qui t'aide vraiment ». */
class BilanTest {

    private static final YearMonth NOVEMBRE = YearMonth.of(2026, 11);

    private static MoodJournal.Entry entree(int jour, int heure, int minute, Mood m, String source) {
        return new MoodJournal.Entry(LocalDateTime.of(2026, 11, jour, heure, minute), m, source);
    }

    private static HistoriqueEcoute.Ecoute ecoute(int jour, int secondes, String artiste) {
        return new HistoriqueEcoute.Ecoute(LocalDateTime.of(2026, 11, jour, 20, 0), secondes, "jamendo-" + jour,
                "pop", artiste, "Titre");
    }

    private static final Mood TENDU = new Mood(-0.7, 0.6), SEREIN = new Mood(0.6, -0.6), TRISTE = new Mood(-0.7, -0.1);

    @Test
    void humeurDominanteEtCalendrier() {
        List<MoodJournal.Entry> journal = List.of(
                entree(3, 10, 0, SEREIN, "pavé d'humeur"),
                entree(3, 18, 0, SEREIN, "pavé d'humeur"),
                entree(5, 9, 0, TENDU, "« stressé »"),
                new MoodJournal.Entry(LocalDateTime.of(2026, 10, 30, 9, 0), TENDU, "pavé d'humeur")); // octobre : ignoré
        Bilan b = Bilan.calculer(NOVEMBRE, journal, List.of());
        assertEquals(3, b.nbMoments());
        assertEquals(Sector.SEREIN, b.dominante().orElseThrow());
        assertEquals(2, b.parJour().size());
        assertEquals(SEREIN, b.parJour().get(LocalDate.of(2026, 11, 3)));
    }

    @Test
    void moisVide() {
        Bilan b = Bilan.calculer(NOVEMBRE, List.of(), List.of());
        assertTrue(b.dominante().isEmpty());
        assertTrue(b.effets().isEmpty());
        assertEquals(0, b.minutes());
    }

    @Test
    void ecouteEtArtistes() {
        List<HistoriqueEcoute.Ecoute> h = List.of(ecoute(1, 180, "Mira"), ecoute(2, 240, "Mira"), ecoute(2, 180, "Hoshi"));
        Bilan b = Bilan.calculer(NOVEMBRE, List.of(), h);
        assertEquals(10, b.minutes());           // 600 s
        assertEquals(3, b.nbEcoutes());
        assertEquals(new Bilan.Classement("Mira", 2), b.topArtistes().get(0));
    }

    @Test
    void ceQuiTAideVraiment() {
        List<MoodJournal.Entry> journal = List.of(
                // 1re fois : tendu -> station -> serein (amélioration)
                entree(4, 18, 0, TENDU, "« stressé par les partiels »"),
                entree(4, 18, 1, new Mood(0.5, -0.7), "station « Lac sans vent »"),
                entree(4, 18, 30, SEREIN, "pavé d'humeur"),
                // 2e fois : triste -> même station -> encore triste (pas d'amélioration)
                entree(6, 21, 0, TRISTE, "pavé d'humeur"),
                entree(6, 21, 1, new Mood(0.5, -0.7), "station « Lac sans vent »"),
                entree(6, 21, 20, TRISTE, "pavé d'humeur"),
                // voyage : l'humeur de fin posée par le voyage ne compte pas, seul le ressenti suivant compte
                entree(8, 8, 0, TRISTE, "pavé d'humeur"),
                entree(8, 8, 1, new Mood(-0.75, -0.35), "début du voyage « Remonter la pente »"),
                entree(8, 8, 6, new Mood(0.75, 0.35), "fin du voyage « Remonter la pente »"),
                entree(8, 8, 10, new Mood(0.4, 0.2), "pavé d'humeur"));
        List<Bilan.Effet> effets = Bilan.calculer(NOVEMBRE, journal, List.of()).effets();

        assertEquals(2, effets.size());
        Bilan.Effet voyage = effets.get(0);                       // 1 sur 1 : le plus efficace
        assertEquals("Remonter la pente", voyage.musique());
        assertEquals(1, voyage.ameliorations());
        assertEquals(1.1, voyage.gainMoyen(), 1e-9);              // -0.7 -> 0.4

        Bilan.Effet station = effets.get(1);
        assertEquals("Lac sans vent", station.musique());
        assertEquals(2, station.mesures());
        assertEquals(1, station.ameliorations());
    }

    @Test
    void sansRessentiApresOnNeMesureRien() {
        List<MoodJournal.Entry> journal = List.of(
                entree(4, 18, 0, TENDU, "pavé d'humeur"),
                entree(4, 18, 1, SEREIN, "station « Lac sans vent »"),
                entree(4, 18, 5, TENDU, "station « Tempête intérieure »"), // autre musique avant tout ressenti
                entree(4, 23, 0, SEREIN, "pavé d'humeur"));                // et trop tard (plus de 3 h)
        assertTrue(Bilan.calculer(NOVEMBRE, journal, List.of()).effets().isEmpty());
    }

    @Test
    void reconnaissanceDesSources() {
        assertEquals("Pluie sur la vitre", Bilan.musique("station « Pluie sur la vitre »").orElseThrow());
        assertEquals("Catégorie Serein", Bilan.musique("catégorie « Serein »").orElseThrow());
        assertTrue(Bilan.musique("fin du voyage « Atterrissage »").isEmpty());
        assertTrue(Bilan.estRessenti("« fatigué mais content »"));
        assertFalse(Bilan.estRessenti("station « Pluie sur la vitre »"));
    }
}
