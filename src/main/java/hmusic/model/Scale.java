package hmusic.model;

/**
 * Les modes musicaux, classés du plus lumineux au plus sombre.
 * La « couleur » émotionnelle d'un morceau dépend énormément du mode choisi.
 */
public enum Scale {
    LYDIEN("Lydien", 0, 2, 4, 6, 7, 9, 11),     // rêveur, flottant
    IONIEN("Majeur", 0, 2, 4, 5, 7, 9, 11),     // joyeux, simple
    MIXOLYDIEN("Mixolydien", 0, 2, 4, 5, 7, 9, 10), // solaire, un peu rock
    DORIEN("Dorien", 0, 2, 3, 5, 7, 9, 10),     // mineur mais optimiste
    EOLIEN("Mineur", 0, 2, 3, 5, 7, 8, 10),     // triste, nostalgique
    PHRYGIEN("Phrygien", 0, 1, 3, 5, 7, 8, 10); // sombre, tendu

    private final String label;
    private final int[] steps;

    Scale(String label, int... steps) {
        this.label = label;
        this.steps = steps;
    }

    public String label() { return label; }

    /**
     * Convertit un degré de la gamme (0 = tonique, 7 = tonique à l'octave, -1 = sous la tonique…)
     * en note MIDI.
     */
    public int pitch(int tonic, int degree) {
        int octave = Math.floorDiv(degree, 7);
        int d = Math.floorMod(degree, 7);
        return tonic + octave * 12 + steps[d];
    }

    /** Choisit le mode le plus adapté à une humeur. */
    public static Scale forMood(Mood m) {
        double v = m.valence(), e = m.energy();
        if (v > 0.5) return e < 0 ? LYDIEN : IONIEN;
        if (v > 0.15) return e < 0.2 ? IONIEN : MIXOLYDIEN;
        if (v > -0.2) return DORIEN;
        if (v > -0.6) return EOLIEN;
        return e > 0.25 ? PHRYGIEN : EOLIEN;
    }

    /** Suites d'accords typiques du mode (en degrés : 0 = I, 4 = V, 5 = vi…). */
    public int[][] progressions() {
        return switch (this) {
            case LYDIEN -> new int[][]{{0, 1, 0, 1}, {0, 1, 5, 4}, {0, 4, 5, 1}};
            case IONIEN -> new int[][]{{0, 4, 5, 3}, {0, 5, 3, 4}, {0, 3, 0, 4}, {5, 3, 0, 4}};
            case MIXOLYDIEN -> new int[][]{{0, 6, 3, 0}, {0, 3, 6, 0}, {0, 4, 6, 3}};
            case DORIEN -> new int[][]{{0, 3, 0, 3}, {0, 6, 3, 3}, {0, 2, 3, 0}};
            case EOLIEN -> new int[][]{{0, 5, 2, 6}, {0, 3, 5, 4}, {0, 6, 5, 6}, {0, 5, 3, 4}};
            case PHRYGIEN -> new int[][]{{0, 1, 0, 1}, {0, 1, 6, 0}, {0, 5, 1, 0}};
        };
    }
}
