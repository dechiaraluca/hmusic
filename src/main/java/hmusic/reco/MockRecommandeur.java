package hmusic.reco;

import hmusic.analysis.MoodAnalyzer;
import hmusic.catalogue.Catalogue;
import hmusic.catalogue.Morceau;
import hmusic.model.Mood;
import hmusic.model.Sector;
import hmusic.stream.MoodTags;

import java.util.*;

/**
 * Recommandeur sans réseau et déterministe (même humeur + même catalogue = même playlist).
 * <ol>
 *   <li>L'humeur écrite est convertie en émotion par l'analyseur de texte de hmusic.</li>
 *   <li>Chaque morceau reçoit un score : +2 par tag d'ambiance correspondant à l'émotion,
 *       +3 si son genre convient à l'émotion.</li>
 *   <li>On garde les meilleurs scores.</li>
 * </ol>
 * Il ne peut proposer que des morceaux du catalogue, puisqu'il les choisit dedans.
 */
public class MockRecommandeur implements Recommandeur {

    /** Genres qui conviennent à chaque famille d'émotion. */
    private static final Map<Sector, Set<String>> GENRES = new EnumMap<>(Sector.class);

    static {
        GENRES.put(Sector.HEUREUX, Set.of("pop", "reggae", "folk", "world"));
        GENRES.put(Sector.EUPHORIQUE, Set.of("electronic", "pop", "hiphop", "dance"));
        GENRES.put(Sector.ELECTRISE, Set.of("electronic", "rock", "hiphop", "soundtrack"));
        GENRES.put(Sector.TENDU, Set.of("metal", "rock", "soundtrack", "electronic"));
        GENRES.put(Sector.TRISTE, Set.of("classical", "blues", "folk", "soundtrack"));
        GENRES.put(Sector.MELANCOLIQUE, Set.of("folk", "blues", "jazz", "classical"));
        GENRES.put(Sector.SOMNOLENT, Set.of("ambient", "classical", "lounge"));
        GENRES.put(Sector.SEREIN, Set.of("ambient", "lounge", "classical", "jazz"));
        GENRES.put(Sector.NEUTRE, Set.of("lounge", "jazz", "pop", "folk"));
    }

    private final MoodAnalyzer analyseur = new MoodAnalyzer();

    @Override
    public Playlist recommander(String humeur, Catalogue catalogue, int taille) throws RecommandationException {
        if (catalogue.estVide()) throw new RecommandationException("Le catalogue est vide.");
        Mood mood = analyseur.analyze(humeur).map(MoodAnalyzer.Analysis::mood).orElse(Mood.NEUTRAL);
        Sector sector = mood.sector();
        Set<String> tagsVoulus = new HashSet<>(Arrays.asList(MoodTags.tags(sector).split("\\+")));
        Set<String> genresVoulus = GENRES.get(sector);

        List<Morceau> tries = new ArrayList<>(catalogue.tous());
        tries.sort(Comparator.comparingInt((Morceau m) -> score(m, tagsVoulus, genresVoulus)).reversed()
                .thenComparing(Morceau::id)); // départage stable : résultat toujours identique
        List<Morceau> choix = tries.subList(0, Math.min(taille, tries.size()));
        return new Playlist(humeur, choix, List.of(), nom());
    }

    static int score(Morceau m, Set<String> tagsVoulus, Set<String> genresVoulus) {
        int s = 0;
        for (String tag : m.tags()) if (tagsVoulus.contains(tag.toLowerCase(Locale.ROOT))) s += 2;
        if (genresVoulus.contains(m.genre())) s += 3;
        return s;
    }

    @Override
    public String nom() {
        return "recommandeur hors ligne";
    }
}
