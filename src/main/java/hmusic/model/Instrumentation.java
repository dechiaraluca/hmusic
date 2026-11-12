package hmusic.model;

/**
 * Un « orchestre » : 4 instruments General MIDI (numéros de programme 0-127)
 * pour la mélodie, la nappe d'accords, la basse et l'arpège.
 */
public enum Instrumentation {
    AQUARELLE("Aquarelle", "vibraphone, nappe chaude, contrebasse, harpe", 11, 89, 32, 46),
    SOLEIL("Soleil", "marimba, cordes, basse, guitare nylon", 12, 49, 33, 24),
    NEON("Néon", "lead synthé, polysynth, basse synthé, piano électrique", 80, 90, 38, 5),
    ORAGE("Orage", "guitare saturée, cordes synthé, basse, cuivres synthé", 30, 50, 34, 62),
    PLUIE("Pluie", "piano, cordes, contrebasse à l'archet, harpe", 0, 48, 43, 46),
    BRUME("Brume", "piano électrique, nappe new age, basse, harpe", 4, 88, 33, 46);

    private final String label;
    private final String description;
    private final int melody, pad, bass, arp;

    Instrumentation(String label, String description, int melody, int pad, int bass, int arp) {
        this.label = label;
        this.description = description;
        this.melody = melody;
        this.pad = pad;
        this.bass = bass;
        this.arp = arp;
    }

    public String label() { return label; }
    public String description() { return description; }
    public int melody() { return melody; }
    public int pad() { return pad; }
    public int bass() { return bass; }
    public int arp() { return arp; }

    /** Un orchestre par quadrant du plan émotionnel (+ un pour le centre). */
    public static Instrumentation forMood(Mood m) {
        if (m.intensity() < 0.2) return BRUME;
        boolean positive = m.valence() >= 0;
        boolean energetic = m.energy() >= 0;
        if (positive && energetic) return m.energy() > 0.45 ? NEON : SOLEIL;
        if (positive) return AQUARELLE;
        if (energetic) return ORAGE;
        return PLUIE;
    }
}
