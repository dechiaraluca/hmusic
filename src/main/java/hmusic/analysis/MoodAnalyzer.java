package hmusic.analysis;

import hmusic.model.Mood;

import java.text.Normalizer;
import java.util.*;

/**
 * Devine une humeur à partir d'une phrase en français (« je suis crevé mais content »).
 * <p>
 * Méthode volontairement simple et explicable : un lexique de racines de mots associées
 * à une valence et une énergie, avec gestion des négations (« pas content ») et des
 * intensificateurs (« très stressé », « un peu fatigué »).
 */
public class MoodAnalyzer {

    public record Analysis(Mood mood, List<String> words) {}

    /** racine → {valence, énergie}. Une racine terminée par « $ » doit correspondre au mot exact. */
    private static final Map<String, double[]> LEXICON = new HashMap<>();
    private static final Map<String, Double> MODIFIERS = new HashMap<>();
    private static final Set<String> NEGATIONS = Set.of("pas", "jamais", "guere", "sans", "aucun", "aucune");
    private static final Set<String> RESETS = Set.of("mais", "et", "pourtant", "cependant", "car", "quand");

    static {
        // agréable + énergique
        w("heureu", .8, .4); w("joie", .8, .5); w("joyeu", .8, .5); w("content", .7, .2);
        w("ravi", .8, .4); w("excit", .6, .9); w("motiv", .6, .8); w("energi", .4, .9);
        w("euphori", .9, .9); w("hype", .6, .9); w("fete", .8, .8); w("danse", .7, .8);
        w("sport", .4, .8); w("courir", .3, .9); w("genial", .9, .6); w("top$", .6, .4);
        w("cool$", .6, .2); w("amoureu", .9, .4); w("amour", .8, .3); w("fier", .7, .5);
        w("confian", .6, .4); w("inspir", .6, .5); w("soleil", .6, .4); w("gagne", .7, .6);
        w("bien$", .5, .1); w("bonheur", .9, .3); w("rire", .7, .6); w("rigol", .7, .6);
        w("vacances", .8, .3); w("week", .6, .3); w("enthousias", .8, .7);
        // agréable + calme
        w("calme", .4, -.6); w("zen$", .5, -.7); w("detendu", .5, -.6); w("relax", .5, -.6);
        w("paisible", .5, -.7); w("serein", .6, -.6); w("apaise", .5, -.6); w("repos", .4, -.7);
        w("doux", .4, -.5); w("douce", .4, -.5); w("tranquil", .4, -.6); w("chill", .5, -.5);
        w("cocon", .5, -.6); w("reve", .4, -.45); w("medit", .4, -.8); w("concentr", .2, -.2);
        w("focus", .2, -.1); w("lire$", .3, -.5); w("lecture", .3, -.5); w("satisf", .6, -.1);
        w("reconnaiss", .6, -.2); w("soulag", .6, -.4);
        // désagréable + calme
        w("fatigu", -.2, -.8); w("epuis", -.4, -.9); w("creve", -.3, -.8); w("dorm", 0, -.9);
        w("sommeil", 0, -.9); w("endormi", 0, -.9); w("triste", -.8, -.4); w("deprim", -.9, -.6);
        w("melancol", -.6, -.5); w("nostalg", -.4, -.4); w("seul", -.6, -.4); w("solitude", -.5, -.4);
        w("ennui", -.4, -.6); w("ennuy", -.4, -.6); w("vide$", -.6, -.6); w("pleur", -.8, -.2);
        w("chagrin", -.8, -.3); w("mal$", -.6, 0); w("malheureu", -.8, -.2); w("nul$", -.6, -.1);
        w("pluie", -.3, -.4); w("gris$", -.3, -.4); w("manque", -.5, -.3); w("decu", -.6, -.2);
        w("malade", -.5, -.6); w("blase", -.3, -.5); w("lassitude", -.4, -.6); w("decourag", -.7, -.5);
        // désagréable + énergique
        w("stress", -.6, .7); w("angoiss", -.7, .6); w("anxi", -.6, .6); w("nerveu", -.5, .7);
        w("enerv", -.7, .8); w("colere", -.8, .9); w("rage", -.9, 1); w("furieu", -.9, .9);
        w("agac", -.5, .5); w("frustr", -.6, .6); w("peur", -.7, .6); w("inquiet", -.6, .5);
        w("panique", -.8, .9); w("examen", -.3, .5); w("partiel", -.3, .5); w("deadline", -.4, .6);
        w("presse", -.3, .6); w("tendu", -.5, .6); w("haine", -.9, .8); w("deteste", -.8, .6);
        w("marre", -.6, .4); w("saoul", -.5, .3); w("debord", -.5, .7); w("surmen", -.5, .6);

        MODIFIERS.put("tres", 1.5); MODIFIERS.put("trop", 1.6); MODIFIERS.put("vraiment", 1.4);
        MODIFIERS.put("tellement", 1.6); MODIFIERS.put("hyper", 1.6); MODIFIERS.put("super", 1.5);
        MODIFIERS.put("grave", 1.4); MODIFIERS.put("completement", 1.6); MODIFIERS.put("totalement", 1.5);
        MODIFIERS.put("archi", 1.6); MODIFIERS.put("peu", 0.5); MODIFIERS.put("legerement", 0.5);
        MODIFIERS.put("assez", 0.8); MODIFIERS.put("plutot", 0.8);
    }

    private static void w(String stem, double valence, double energy) {
        LEXICON.put(stem, new double[]{valence, energy});
    }

    public Optional<Analysis> analyze(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String norm = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // retire les accents
        String[] tokens = norm.split("[^a-z0-9]+");

        double sumV = 0, sumE = 0;
        int count = 0;
        double modifier = 1;
        int lastNegation = -10;
        List<String> found = new ArrayList<>();

        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];
            if (t.isEmpty()) continue;
            if (NEGATIONS.contains(t)) { lastNegation = i; continue; }
            if (RESETS.contains(t)) { lastNegation = -10; modifier = 1; continue; }
            Double m = MODIFIERS.get(t);
            if (m != null) { modifier *= m; continue; }

            String stem = match(t);
            if (stem == null) continue;
            double[] ve = LEXICON.get(stem);
            double v = ve[0], e = ve[1];
            boolean negated = i - lastNegation <= 3;
            if (negated) {           // « pas content » ≈ un peu triste, « pas fatigué » ≈ un peu en forme
                v = -v * 0.6;
                e = -e * 0.4;
                lastNegation = -10;
            }
            sumV += v * modifier;
            sumE += e * modifier;
            count++;
            found.add((negated ? "pas " : "") + t);
            modifier = 1;
        }
        if (count == 0) return Optional.empty();
        return Optional.of(new Analysis(new Mood(sumV / count * 1.15, sumE / count * 1.15), found));
    }

    private static String match(String token) {
        String best = null;
        for (String stem : LEXICON.keySet()) {
            boolean ok = stem.endsWith("$")
                    ? token.equals(stem.substring(0, stem.length() - 1))
                    : token.startsWith(stem);
            if (ok && (best == null || stem.length() > best.length())) best = stem;
        }
        return best;
    }
}
