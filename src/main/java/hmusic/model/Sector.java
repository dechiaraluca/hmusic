package hmusic.model;

import java.awt.Color;

/**
 * Les 8 grandes familles d'émotions du modèle circomplexe de Russell (1980),
 * plus une zone « neutre » au centre.
 * <p>
 * Chaque secteur occupe 45° autour du centre du plan valence / énergie,
 * en partant de la droite (valence positive) et en tournant dans le sens trigonométrique.
 */
public enum Sector {
    HEUREUX("Heureux", "Lumineux et léger", 0xFFC94A),
    EUPHORIQUE("Euphorique", "Tout semble possible", 0xFF7A59),
    ELECTRISE("Électrisé", "Adrénaline pure", 0xFF3D8B),
    TENDU("Tendu", "Sous pression", 0xE5383B),
    TRISTE("Triste", "Le cœur un peu lourd", 0x3A7BD5),
    MELANCOLIQUE("Mélancolique", "Douce nostalgie", 0x6A5AE0),
    SOMNOLENT("Somnolent", "Au ralenti", 0x9B87D9),
    SEREIN("Serein", "Calme intérieur", 0x2EC4B6),
    NEUTRE("Neutre", "Ni haut ni bas", 0x9AA0A6);

    /** Les secteurs « orientés » (sans NEUTRE), dans l'ordre des angles 0°, 45°, 90°… */
    public static final Sector[] RING = {
            HEUREUX, EUPHORIQUE, ELECTRISE, TENDU, TRISTE, MELANCOLIQUE, SOMNOLENT, SEREIN
    };

    private final String label;
    private final String description;
    private final Color color;

    Sector(String label, String description, int rgb) {
        this.label = label;
        this.description = description;
        this.color = new Color(rgb);
    }

    public String label() { return label; }

    public String description() { return description; }

    public Color color() { return color; }
}
