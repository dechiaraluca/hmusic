package hmusic.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Réglages persistants de l'utilisateur (~/.hmusic/settings.properties). */
public class Settings {

    private static final String JAMENDO_KEY = "jamendo.clientId";

    private final Path file;
    private final Properties props = new Properties();

    public Settings() {
        this(Path.of(System.getProperty("user.home"), ".hmusic", "settings.properties"));
    }

    public Settings(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException e) {
                System.err.println("Réglages illisibles : " + e.getMessage());
            }
        }
    }

    /** Le Client ID Jamendo : variable d'environnement JAMENDO_CLIENT_ID, sinon fichier de réglages. */
    public synchronized String jamendoClientId() {
        String env = System.getenv("JAMENDO_CLIENT_ID");
        if (env != null && !env.isBlank()) return env.trim();
        String v = props.getProperty(JAMENDO_KEY, "").trim();
        return v.isEmpty() ? null : v;
    }

    public synchronized void setJamendoClientId(String id) {
        props.setProperty(JAMENDO_KEY, id == null ? "" : id.trim());
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                props.store(w, "Réglages hmusic");
            }
        } catch (IOException e) {
            System.err.println("Impossible d'enregistrer les réglages : " + e.getMessage());
        }
    }
}
