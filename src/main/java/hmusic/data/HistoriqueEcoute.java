package hmusic.data;

import hmusic.catalogue.Morceau;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Historique des morceaux écoutés en mode Écouter (un fichier CSV par utilisateur dans ~/.hmusic).
 * Il sert au bilan du mois (artistes les plus écoutés, temps d'écoute).
 * <p>
 * Une ligne par morceau écouté au moins {@value #SECONDES_MIN} secondes :
 * {@code date;secondes;identifiant;genre;artiste;titre}. Le titre est en dernier : même s'il
 * contenait un « ; », la lecture avec {@code split(";", 6)} resterait correcte.
 */
public class HistoriqueEcoute {

    /** En dessous, on considère que le morceau n'a pas vraiment été écouté. */
    public static final int SECONDES_MIN = 30;

    public record Ecoute(LocalDateTime date, int secondes, String id, String genre, String artiste, String titre) {}

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path fichier;
    private final List<Ecoute> ecoutes = new ArrayList<>();

    public HistoriqueEcoute(Path fichier) {
        this.fichier = fichier;
        charger();
    }

    private void charger() {
        if (!Files.exists(fichier)) return;
        try {
            for (String ligne : Files.readAllLines(fichier, StandardCharsets.UTF_8)) {
                String[] p = ligne.split(";", 6);
                if (p.length < 6) continue;
                try {
                    ecoutes.add(new Ecoute(LocalDateTime.parse(p[0], ISO), Integer.parseInt(p[1]), p[2], p[3], p[4], p[5]));
                } catch (RuntimeException ignored) {
                    // ligne abîmée : ignorée
                }
            }
        } catch (IOException e) {
            System.err.println("Historique illisible : " + e.getMessage());
        }
    }

    /** Enregistre une écoute (ignorée si elle a duré moins de {@value #SECONDES_MIN} s). */
    public synchronized void ajouter(Morceau m, double secondes) {
        if (secondes < SECONDES_MIN) return;
        Ecoute e = new Ecoute(LocalDateTime.now().withNano(0), (int) Math.round(secondes), m.id(), m.genre(),
                propre(m.artiste()), propre(m.titre()));
        ecoutes.add(e);
        String ligne = String.join(";", ISO.format(e.date()), String.valueOf(e.secondes()), e.id(), e.genre(),
                e.artiste(), e.titre()) + System.lineSeparator();
        try {
            Files.createDirectories(fichier.getParent());
            // ajout en fin de fichier : pas besoin de tout réécrire à chaque morceau
            Files.writeString(fichier, ligne, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            System.err.println("Impossible d'écrire l'historique : " + ex.getMessage());
        }
    }

    public synchronized List<Ecoute> ecoutes() {
        return List.copyOf(ecoutes);
    }

    private static String propre(String s) {
        return s.replaceAll("[;\\r\\n]", " ").trim();
    }
}
