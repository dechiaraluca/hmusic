package hmusic.model;

import java.util.List;

/**
 * Un « voyage guidé » : la musique part de l'humeur actuelle et glisse lentement
 * vers une humeur cible.
 * <p>
 * C'est le principe d'iso-modulation utilisé en musicothérapie : on commence par une
 * musique qui « rejoint » l'émotion de la personne, puis on la fait évoluer progressivement
 * (passer brutalement d'une musique joyeuse à quelqu'un de triste fonctionne mal).
 */
public record Journey(String name, String description, Mood from, Mood to, int seconds) {

    public static final List<Journey> PRESETS = List.of(
            new Journey("Décompresser", "du stress vers la sérénité",
                    new Mood(-0.65, 0.75), new Mood(0.55, -0.6), 240),
            new Journey("Remonter la pente", "de la tristesse vers la joie",
                    new Mood(-0.75, -0.35), new Mood(0.75, 0.35), 300),
            new Journey("Réveil en douceur", "du sommeil vers l'énergie",
                    new Mood(0.1, -0.9), new Mood(0.5, 0.8), 240),
            new Journey("Atterrissage", "de l'euphorie vers le sommeil",
                    new Mood(0.8, 0.8), new Mood(0.05, -0.9), 300),
            new Journey("Évacuer la colère", "de la rage vers le calme",
                    new Mood(-0.9, 0.9), new Mood(0.3, -0.45), 240)
    );
}
