package hmusic.data;

import hmusic.model.Mood;
import hmusic.model.Sector;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Le bilan d'un mois (« hmusic Wrapped »), calculé à partir du journal d'humeur et de l'historique
 * d'écoute. Aucune interface ici : tout se teste avec JUnit.
 *
 * @param mois           le mois concerné
 * @param nbMoments      nombre d'humeurs enregistrées dans le mois
 * @param dominante      la famille d'émotion la plus fréquente (vide si aucune donnée)
 * @param parJour        humeur moyenne de chaque jour où il y a au moins une entrée
 * @param minutes        temps d'écoute en mode Écouter
 * @param nbEcoutes      nombre de morceaux écoutés
 * @param topArtistes    artistes les plus écoutés (3 maximum)
 * @param musiquePreferee station, voyage ou catégorie la plus lancée (vide si aucune)
 * @param effets         « ce qui t'aide vraiment », du plus efficace au moins efficace
 */
public record Bilan(YearMonth mois, int nbMoments, Optional<Sector> dominante, SortedMap<LocalDate, Mood> parJour,
                    int minutes, int nbEcoutes, List<Classement> topArtistes, Optional<String> musiquePreferee,
                    List<Effet> effets) {

    /** Un élément classé avec son nombre d'occurrences. */
    public record Classement(String nom, int nombre) {}

    /**
     * L'effet mesuré d'une musique (station, voyage, catégorie…) sur l'humeur.
     *
     * @param mesures       nombre de fois où on a pu comparer l'humeur avant / après
     * @param ameliorations nombre de fois où l'humeur est devenue plus agréable
     * @param gainMoyen     évolution moyenne de la valence (de -2 à +2)
     */
    public record Effet(String musique, int mesures, int ameliorations, double gainMoyen) {}

    /** Écart maximal entre une musique et les humeurs déclarées juste avant / juste après. */
    static final Duration FENETRE = Duration.ofHours(3);
    /** En dessous de ce gain de valence, on ne parle pas d'amélioration (petites hésitations sur le pavé). */
    static final double SEUIL_AMELIORATION = 0.1;

    public static Bilan calculer(YearMonth mois, List<MoodJournal.Entry> journal, List<HistoriqueEcoute.Ecoute> historique) {
        List<MoodJournal.Entry> entrees = journal.stream()
                .filter(e -> YearMonth.from(e.time()).equals(mois))
                .sorted(Comparator.comparing(MoodJournal.Entry::time))
                .toList();
        List<HistoriqueEcoute.Ecoute> ecoutes = historique.stream()
                .filter(e -> YearMonth.from(e.date()).equals(mois))
                .toList();

        // humeur dominante et humeur moyenne de chaque jour
        Map<Sector, Integer> parSecteur = new EnumMap<>(Sector.class);
        Map<LocalDate, double[]> sommes = new TreeMap<>(); // jour -> {somme valence, somme énergie, nombre}
        for (MoodJournal.Entry e : entrees) {
            parSecteur.merge(e.mood().sector(), 1, Integer::sum);
            double[] s = sommes.computeIfAbsent(e.time().toLocalDate(), j -> new double[3]);
            s[0] += e.mood().valence();
            s[1] += e.mood().energy();
            s[2]++;
        }
        SortedMap<LocalDate, Mood> parJour = new TreeMap<>();
        sommes.forEach((jour, s) -> parJour.put(jour, new Mood(s[0] / s[2], s[1] / s[2])));
        Optional<Sector> dominante = parSecteur.entrySet().stream()
                .max(Map.Entry.<Sector, Integer>comparingByValue().thenComparing(e -> -e.getKey().ordinal()))
                .map(Map.Entry::getKey);

        // écoute
        int secondes = ecoutes.stream().mapToInt(HistoriqueEcoute.Ecoute::secondes).sum();
        List<Classement> topArtistes = top(ecoutes.stream().map(HistoriqueEcoute.Ecoute::artiste).toList(), 3);

        // musique préférée : la station / le voyage / la catégorie la plus lancée
        List<String> musiques = entrees.stream().map(e -> musique(e.source())).flatMap(Optional::stream).toList();
        Optional<String> preferee = top(musiques, 1).stream().findFirst().map(Classement::nom);

        return new Bilan(mois, entrees.size(), dominante, parJour, (int) Math.round(secondes / 60.0), ecoutes.size(),
                topArtistes, preferee, effets(entrees));
    }

    /**
     * « Ce qui t'aide vraiment ». Pour chaque musique lancée, on compare :
     * <ul>
     *   <li>la dernière humeur que <b>tu as déclarée toi-même</b> juste avant (pavé ou phrase) ;</li>
     *   <li>la première humeur que tu as déclarée juste après, avant de lancer une autre musique.</li>
     * </ul>
     * Les humeurs posées automatiquement (début/fin de voyage, station) ne comptent pas : ce sont
     * celles de la musique, pas les tiennes.
     */
    static List<Effet> effets(List<MoodJournal.Entry> entrees) {
        Map<String, List<Double>> gains = new LinkedHashMap<>();
        for (int i = 0; i < entrees.size(); i++) {
            Optional<String> musique = musique(entrees.get(i).source());
            if (musique.isEmpty()) continue;
            MoodJournal.Entry lancement = entrees.get(i);

            MoodJournal.Entry avant = null;
            for (int j = i - 1; j >= 0; j--) {
                MoodJournal.Entry e = entrees.get(j);
                if (Duration.between(e.time(), lancement.time()).compareTo(FENETRE) > 0) break;
                if (estRessenti(e.source())) {
                    avant = e;
                    break;
                }
            }
            MoodJournal.Entry apres = null;
            for (int k = i + 1; k < entrees.size(); k++) {
                MoodJournal.Entry e = entrees.get(k);
                if (Duration.between(lancement.time(), e.time()).compareTo(FENETRE) > 0) break;
                if (musique(e.source()).isPresent()) break;          // une autre musique a pris le relais
                if (estRessenti(e.source())) {
                    apres = e;
                    break;
                }
            }
            if (avant != null && apres != null) {
                gains.computeIfAbsent(musique.get(), m -> new ArrayList<>())
                        .add(apres.mood().valence() - avant.mood().valence());
            }
        }
        List<Effet> effets = new ArrayList<>();
        gains.forEach((musique, liste) -> {
            int mieux = (int) liste.stream().filter(g -> g > SEUIL_AMELIORATION).count();
            double moyenne = liste.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            effets.add(new Effet(musique, liste.size(), mieux, moyenne));
        });
        // les plus efficaces d'abord : proportion d'améliorations, puis gain moyen
        effets.sort(Comparator.comparingDouble((Effet e) -> (double) e.ameliorations() / e.mesures())
                .thenComparingDouble(Effet::gainMoyen).reversed());
        return effets;
    }

    /**
     * Reconnaît une musique lancée par l'utilisateur dans le texte du journal.
     * Ex. : « station « Pluie sur la vitre » » → « Pluie sur la vitre ».
     */
    static Optional<String> musique(String source) {
        for (String prefixe : new String[]{"station « ", "début du voyage « ", "catégorie « "}) {
            if (source.startsWith(prefixe) && source.endsWith(" »")) {
                String nom = source.substring(prefixe.length(), source.length() - 2);
                return Optional.of(prefixe.startsWith("catégorie") ? "Catégorie " + nom : nom);
            }
        }
        return Optional.empty();
    }

    /** Une humeur déclarée par l'utilisateur lui-même : clic sur le pavé, ou phrase écrite (« … »). */
    static boolean estRessenti(String source) {
        return source.equals("pavé d'humeur") || source.startsWith("« ");
    }

    private static List<Classement> top(List<String> noms, int max) {
        Map<String, Integer> compte = new HashMap<>();
        for (String n : noms) compte.merge(n, 1, Integer::sum);
        return compte.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(max)
                .map(e -> new Classement(e.getKey(), e.getValue()))
                .toList();
    }
}
