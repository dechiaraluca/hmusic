package hmusic.model;

/**
 * La « traduction » d'une humeur en paramètres musicaux.
 * C'est le cœur de l'idée : chaque émotion devient des règles de composition.
 *
 * @param bpm          tempo (plus d'énergie = plus rapide)
 * @param scale        mode musical (plus de valence = plus lumineux)
 * @param instruments  orchestre utilisé
 * @param density      0..1, quantité de notes jouées
 * @param drums        0..1, présence de la batterie (0 = pas de batterie)
 * @param swing        0..0.2, décalage rythmique « groovy »
 * @param reverb       0..127, quantité de réverbération (calme = plus d'espace)
 * @param baseVelocity 0..127, force de frappe moyenne
 */
public record MusicalProfile(int bpm, Scale scale, Instrumentation instruments, double density,
                             double drums, double swing, int reverb, int baseVelocity) {

    public static MusicalProfile from(Mood m) {
        double e01 = (m.energy() + 1) / 2.0;                 // énergie ramenée entre 0 et 1
        int bpm = (int) Math.round(64 + e01 * 76);           // 64 → 140 BPM
        double density = 0.15 + e01 * 0.8;
        double drums = m.energy() < -0.45 ? 0 : (m.energy() + 0.45) / 1.45;
        double swing = (m.valence() > 0.2 && m.energy() > -0.5 && m.energy() < 0.5) ? 0.16 : 0.0;
        int reverb = (int) Math.round(115 - e01 * 75);
        int velocity = (int) Math.round(58 + e01 * 50);
        return new MusicalProfile(bpm, Scale.forMood(m), Instrumentation.forMood(m),
                density, drums, swing, reverb, velocity);
    }
}
