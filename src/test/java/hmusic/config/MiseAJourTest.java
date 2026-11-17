package hmusic.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MiseAJourTest {

    @Test
    void compareLesVersionsMorceauParMorceau() {
        assertTrue(MiseAJour.estPlusRecente("2.4", "2.3"));
        assertTrue(MiseAJour.estPlusRecente("2.10", "2.9"));
        assertTrue(MiseAJour.estPlusRecente("3.0", "2.9.9"));
        assertTrue(MiseAJour.estPlusRecente("2.4.1", "2.4"));
    }

    @Test
    void uneVersionEgaleOuPlusAncienneNEstPasProposee() {
        assertFalse(MiseAJour.estPlusRecente("2.3", "2.3"));
        assertFalse(MiseAJour.estPlusRecente("2.3", "2.4"));
        assertFalse(MiseAJour.estPlusRecente("2.4", "2.4.0"));
    }

    @Test
    void ignoreLesSuffixes() {
        assertTrue(MiseAJour.estPlusRecente("2.5-beta", "2.4"));
    }

    @Test
    void reconnaitUneInstallationDepuisLeStore() {
        assertTrue(MiseAJour.estDansWindowsApps(
                "/C:/Program%20Files/WindowsApps/12345Luca.hmusic_2.4.0.0_x64__abc/app/hmusic.jar"));
        assertFalse(MiseAJour.estDansWindowsApps("/C:/Users/luca/Downloads/hmusic/app/hmusic.jar"));
    }

    @Test
    void laVersionVientDuPom() {
        // les tests tournent après le filtrage des ressources par Maven
        assertNotEquals("dev", MiseAJour.versionActuelle());
    }
}
