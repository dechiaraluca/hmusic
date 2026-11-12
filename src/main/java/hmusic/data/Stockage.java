package hmusic.data;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/**
 * Persistance par sérialisation Java : on écrit un objet (et tout ce qu'il contient) dans un fichier,
 * puis on le relit tel quel au lancement suivant. Pas de base de données.
 * <p>
 * Les fichiers sont rangés dans le dossier personnel de l'utilisateur ({@code ~/.hmusic}),
 * jamais dans le dossier du projet.
 */
public final class Stockage {

    public static final Path DOSSIER = Path.of(System.getProperty("user.home"), ".hmusic");

    private Stockage() {}

    /** Relit un objet sérialisé, ou renvoie {@code parDefaut} si le fichier n'existe pas ou est illisible. */
    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T charger(Path fichier, Supplier<T> parDefaut) {
        if (!Files.exists(fichier)) return parDefaut.get();
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(fichier)))) {
            return (T) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            System.err.println("Fichier illisible, on repart de zéro : " + fichier + " (" + e.getMessage() + ")");
            return parDefaut.get();
        }
    }

    /**
     * Écrit l'objet dans un fichier temporaire puis le renomme : si le programme plante pendant
     * l'écriture, l'ancien fichier reste intact.
     */
    public static void sauvegarder(Path fichier, Serializable objet) throws IOException {
        Files.createDirectories(fichier.getParent());
        Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            out.writeObject(objet);
        }
        Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
    }
}
