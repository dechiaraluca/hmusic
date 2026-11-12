package hmusic.data;

import hmusic.model.Mood;
import hmusic.model.Sector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Journal d'humeur persistant (fichier CSV dans ~/.hmusic/journal.csv).
 * Permet de visualiser son humeur au fil des jours.
 */
public class MoodJournal {

    public record Entry(LocalDateTime time, Mood mood, String source) {}

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Duration MERGE_WINDOW = Duration.ofSeconds(20);

    private final Path file;
    private final List<Entry> entries = new ArrayList<>();

    public MoodJournal() {
        this(Path.of(System.getProperty("user.home"), ".hmusic", "journal.csv"));
    }

    public MoodJournal(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] p = line.split(";", 4);
                if (p.length < 4) continue;
                try {
                    entries.add(new Entry(LocalDateTime.parse(p[0], ISO),
                            new Mood(Double.parseDouble(p[1]), Double.parseDouble(p[2])), p[3]));
                } catch (RuntimeException ignored) {
                    // ligne corrompue : ignorée
                }
            }
        } catch (IOException e) {
            System.err.println("Journal illisible : " + e.getMessage());
        }
    }

    /** Ajoute une entrée (fusionnée avec la précédente si elle date de moins de 20 s). */
    public synchronized void add(Mood mood, String source) {
        Entry e = new Entry(LocalDateTime.now().withNano(0), mood, clean(source));
        if (!entries.isEmpty()) {
            Entry last = entries.get(entries.size() - 1);
            if (Duration.between(last.time(), e.time()).compareTo(MERGE_WINDOW) < 0) {
                entries.remove(entries.size() - 1);
            }
        }
        entries.add(e);
        save();
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public synchronized List<Mood> recentMoods(int max) {
        List<Mood> list = new ArrayList<>();
        for (int i = Math.max(0, entries.size() - max); i < entries.size(); i++) list.add(entries.get(i).mood());
        return list;
    }

    public synchronized Optional<Sector> dominantSince(LocalDateTime since) {
        Map<Sector, Integer> counts = new EnumMap<>(Sector.class);
        for (Entry e : entries) {
            if (e.time().isAfter(since)) counts.merge(e.mood().sector(), 1, Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
    }

    public synchronized void clear() {
        entries.clear();
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("Impossible d'effacer le journal : " + e.getMessage());
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(String.format(Locale.ROOT, "%s;%.3f;%.3f;%s%n",
                    ISO.format(e.time()), e.mood().valence(), e.mood().energy(), e.source()));
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("Impossible d'écrire le journal : " + ex.getMessage());
        }
    }

    private static String clean(String s) {
        if (s == null) return "";
        String c = s.replaceAll("[;\\r\\n]", " ").trim();
        return c.length() > 80 ? c.substring(0, 79) + "…" : c;
    }
}
