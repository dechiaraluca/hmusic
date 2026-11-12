package hmusic.stream;

import hmusic.model.Mood;
import hmusic.model.Sector;

/**
 * Traduit une humeur en critères de recherche Jamendo :
 * des « tags » d'ambiance (selon la famille d'émotion) et une vitesse (selon l'énergie).
 */
public final class MoodTags {

    private MoodTags() {}

    /** Tags Jamendo (séparés par « + » = OU logique avec le paramètre fuzzytags). */
    public static String tags(Sector s) {
        return switch (s) {
            case HEUREUX -> "happy+uplifting+upbeat+positive+feelgood";
            case EUPHORIQUE -> "party+dance+happy+energetic+fun";
            case ELECTRISE -> "energetic+powerful+electronic+motivational+epic";
            case TENDU -> "dark+aggressive+intense+angry+tension";
            case TRISTE -> "sad+emotional+melancholic+heartbreak";
            case MELANCOLIQUE -> "melancholic+nostalgic+dreamy+sad+emotional";
            case SOMNOLENT -> "sleep+ambient+calm+soft+lullaby";
            case SEREIN -> "relaxing+peaceful+calm+chill+meditation";
            case NEUTRE -> "chill+lounge+lofi+acoustic";
        };
    }

    /** Vitesse Jamendo (verylow … veryhigh) déduite de l'énergie. */
    public static String speed(Mood m) {
        double e = m.energy();
        if (e < -0.5) return "verylow+low";
        if (e < 0) return "low+medium";
        if (e < 0.5) return "medium+high";
        return "high+veryhigh";
    }
}
