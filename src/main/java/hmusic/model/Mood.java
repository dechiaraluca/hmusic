package hmusic.model;

import java.awt.Color;

/**
 * Une humeur = un point dans le plan émotionnel de Russell.
 *
 * @param valence de -1 (désagréable) à +1 (agréable)
 * @param energy  de -1 (calme, fatigué) à +1 (excité, énergique)
 */
public record Mood(double valence, double energy) {

    public static final Mood NEUTRAL = new Mood(0, 0);

    public Mood {
        valence = clamp(valence);
        energy = clamp(energy);
    }

    private static double clamp(double x) {
        if (Double.isNaN(x)) return 0;
        return Math.max(-1.0, Math.min(1.0, x));
    }

    /** Distance au centre : 0 = neutre, 1 = émotion très marquée. */
    public double intensity() {
        return Math.min(1.0, Math.hypot(valence, energy));
    }

    /** Angle en degrés dans [0, 360[ (0° = agréable, 90° = énergique). */
    public double angle() {
        double a = Math.toDegrees(Math.atan2(energy, valence));
        return a < 0 ? a + 360 : a;
    }

    public Sector sector() {
        if (intensity() < 0.2) return Sector.NEUTRE;
        int i = (int) Math.round(angle() / 45.0) % 8;
        return Sector.RING[i];
    }

    /** Interpolation linéaire vers une autre humeur (t entre 0 et 1). */
    public Mood lerp(Mood to, double t) {
        return new Mood(valence + (to.valence - valence) * t, energy + (to.energy - energy) * t);
    }

    /** Couleur continue de l'humeur : on mélange les couleurs des deux secteurs voisins. */
    public Color color() {
        double a = angle();
        int i = (int) Math.floor(a / 45.0) % 8;
        double f = (a - i * 45.0) / 45.0;
        Color c = mix(Sector.RING[i].color(), Sector.RING[(i + 1) % 8].color(), f);
        double saturation = Math.min(1.0, intensity() * 1.4);
        return mix(Sector.NEUTRE.color(), c, saturation);
    }

    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
