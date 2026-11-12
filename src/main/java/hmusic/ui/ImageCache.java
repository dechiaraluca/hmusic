package hmusic.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Télécharge les pochettes en arrière-plan et les garde en mémoire (cache LRU). */
public final class ImageCache {

    private static final int MAX = 80;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "hmusic-images");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, BufferedImage> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) { return size() > MAX; }
    };
    private static final Map<String, List<Consumer<BufferedImage>>> PENDING = new HashMap<>();

    private ImageCache() {}

    /** Appelle {@code done} sur le thread Swing avec l'image (ou null si échec). À appeler depuis le thread Swing. */
    public static void load(String url, Consumer<BufferedImage> done) {
        if (url == null || url.isBlank()) {
            done.accept(null);
            return;
        }
        BufferedImage cached = CACHE.get(url);
        if (cached != null) {
            done.accept(cached);
            return;
        }
        List<Consumer<BufferedImage>> waiting = PENDING.get(url);
        if (waiting != null) {
            waiting.add(done);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(done);
        PENDING.put(url, waiting);
        POOL.submit(() -> {
            BufferedImage img = null;
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
                HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() == 200) img = ImageIO.read(new ByteArrayInputStream(resp.body()));
            } catch (Exception ignored) {
                // pas de pochette : on affichera la pochette générative
            }
            final BufferedImage result = img;
            SwingUtilities.invokeLater(() -> {
                if (result != null) CACHE.put(url, result);
                List<Consumer<BufferedImage>> list = PENDING.remove(url);
                if (list != null) list.forEach(c -> c.accept(result));
            });
        });
    }
}
