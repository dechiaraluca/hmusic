package hmusic.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/** Invente un titre poétique pour chaque morceau généré, selon l'humeur. */
public final class TitleGenerator {

    private static final Map<Sector, String[][]> PARTS = new EnumMap<>(Sector.class);

    static {
        PARTS.put(Sector.HEUREUX, new String[][]{
                {"Citronnade", "Vélo", "Terrasse", "Bulles", "Marelle", "Cerises"},
                {"au soleil", "de juillet", "sans raison", "en couleurs", "du dimanche", "à la plage"}});
        PARTS.put(Sector.EUPHORIQUE, new String[][]{
                {"Feux d'artifice", "Confettis", "Fusée", "Grand huit", "Néons", "Fanfare"},
                {"sur le toit", "à minuit", "en orbite", "pour toujours", "dans la foule", "au réveil"}});
        PARTS.put(Sector.ELECTRISE, new String[][]{
                {"Course", "Voltage", "Turbo", "Éclair", "Impulsion", "Sprint"},
                {"dans la nuit", "à pleine vitesse", "sans frein", "sous haute tension", "au bord du ciel", "sur l'autoroute"}});
        PARTS.put(Sector.TENDU, new String[][]{
                {"Orage", "Sirènes", "Engrenages", "Vertige", "Tempête", "Labyrinthe"},
                {"sous le crâne", "en béton", "à l'horizon", "de minuit", "sans sortie", "intérieur"}});
        PARTS.put(Sector.TRISTE, new String[][]{
                {"Pluie", "Lettre", "Quai", "Écho", "Valise", "Parapluie"},
                {"sur la vitre", "sans réponse", "de gare", "dans le vide", "d'hiver", "du dernier train"}});
        PARTS.put(Sector.MELANCOLIQUE, new String[][]{
                {"Polaroid", "Brume", "Souvenir", "Vieux vinyle", "Cerf-volant", "Carte postale"},
                {"d'automne", "en sépia", "de grand-mère", "d'enfance", "au grenier", "de vacances"}});
        PARTS.put(Sector.SOMNOLENT, new String[][]{
                {"Plaid", "Nuage", "Berceuse", "Veilleuse", "Chat", "Oreiller"},
                {"en coton", "pour astronautes", "de 3h du matin", "sous la couette", "sur le radiateur", "en apesanteur"}});
        PARTS.put(Sector.SEREIN, new String[][]{
                {"Lac", "Jardin", "Thé vert", "Marée", "Forêt", "Bambou"},
                {"sans vent", "au crépuscule", "à marée basse", "sous les pins", "du matin", "au ralenti"}});
        PARTS.put(Sector.NEUTRE, new String[][]{
                {"Ligne", "Trajet", "Fenêtre", "Carnet", "Horizon", "Tram"},
                {"du mardi", "de passage", "sans histoire", "en pointillés", "au coin de la rue", "de 8h12"}});
    }

    private TitleGenerator() {}

    public static String generate(Sector sector, Random rnd) {
        String[][] p = PARTS.get(sector);
        return p[0][rnd.nextInt(p[0].length)] + " " + p[1][rnd.nextInt(p[1].length)];
    }
}
