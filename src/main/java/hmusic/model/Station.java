package hmusic.model;

import java.util.List;

/** Une « station » = une humeur prédéfinie, comme une playlist infinie. */
public record Station(String name, String tagline, Mood mood) {

    public static final List<Station> PRESETS = List.of(
            new Station("Lever de soleil", "Douceur et lumière", new Mood(0.6, 0.1)),
            new Station("Fête sur le toit", "Euphorie collective", new Mood(0.85, 0.7)),
            new Station("Course nocturne", "Néons et adrénaline", new Mood(0.3, 0.9)),
            new Station("Tempête intérieure", "Laisser sortir la rage", new Mood(-0.75, 0.8)),
            new Station("Pluie sur la vitre", "Mélancolie au chaud", new Mood(-0.7, -0.2)),
            new Station("Vieux polaroids", "Nostalgie douce", new Mood(-0.45, -0.6)),
            new Station("Focus profond", "Pour réviser", new Mood(0.1, -0.35)),
            new Station("Lac sans vent", "Respirer lentement", new Mood(0.55, -0.75)),
            new Station("Insomnie douce", "Pour s'endormir", new Mood(0.0, -0.95))
    );
}
