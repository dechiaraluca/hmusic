package hmusic.stream;

import hmusic.catalogue.Morceau;
import hmusic.config.Settings;
import hmusic.model.Mood;
import hmusic.model.Sector;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Le « cerveau » du mode Écouter : construit une file d'attente de vrais morceaux
 * correspondant à l'humeur, la renouvelle quand l'humeur change de famille,
 * et enchaîne les morceaux.
 * <p>
 * Toutes les méthodes publiques s'utilisent depuis le thread Swing, et toutes les
 * notifications aux vues arrivent aussi sur le thread Swing.
 */
public class ListenController implements StreamPlayer.Listener {

    /** Ce que l'interface doit afficher. Toutes les méthodes sont appelées sur le thread Swing. */
    public interface View {
        default void showNeedsKey(String error) {}

        default void showReady() {}

        default void showStatus(String message, boolean error) {}

        default void showNowPlaying(Morceau track) {}

        default void showQueue(List<Morceau> upcoming) {}

        default void showProgress(Morceau track, double seconds) {}

        default void showSpectrum(float[] bands) {}

        default void showPlaying(boolean playing) {}
    }

    private static final int PAGE = 30;
    private static final int LOW_WATER = 3;

    private final Settings settings;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final StreamPlayer player = new StreamPlayer(http, this);
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hmusic-jamendo");
        t.setDaemon(true);
        return t;
    });
    private final List<View> views = new ArrayList<>();
    private final Deque<Morceau> queue = new ArrayDeque<>();
    private final Set<String> played = new HashSet<>();
    private final Random rnd = new Random();
    private final Timer debounce;

    private JamendoClient client;
    private Mood mood = Mood.NEUTRAL;
    private Sector queueSector;       // famille d'humeur de la file actuelle
    private int page;                 // page de résultats Jamendo pour cette famille
    private int generation;           // invalide les recherches devenues obsolètes
    private boolean fetching;
    private boolean playRequested;    // l'utilisateur veut entendre quelque chose
    private boolean active;           // le mode Écouter est-il affiché ?
    private Morceau current;
    private int consecutiveErrors;
    /** Nom de la playlist en cours (playlist IA, artiste…), ou null en mode « radio d'humeur ». */
    private String playlist;

    public ListenController(Settings settings) {
        this.settings = settings;
        String id = settings.jamendoClientId();
        if (id != null) client = new JamendoClient(id, http);
        debounce = new Timer(700, e -> {
            if (active && mood.sector() != queueSector) refill(false);
        });
        debounce.setRepeats(false);
    }

    public void addView(View v) {
        views.add(v);
    }

    public void removeView(View v) {
        views.remove(v);
    }

    /** Le client Jamendo, s'il est configuré (utilisé par l'Admin pour importer le catalogue). */
    public Optional<JamendoClient> jamendo() {
        return Optional.ofNullable(client);
    }

    public boolean inPlaylist() {
        return playlist != null;
    }

    public boolean hasKey() {
        return client != null;
    }

    public boolean isPlaying() {
        return playRequested && current != null && !player.isPaused();
    }

    public Morceau current() {
        return current;
    }

    public List<Morceau> upcoming() {
        return List.copyOf(queue);
    }

    // ================================================================== clé API

    /** Enregistre un Client ID puis vérifie qu'il fonctionne en lançant une recherche. */
    public void setClientId(String id) {
        if (id == null || id.isBlank()) {
            views.forEach(v -> v.showNeedsKey("Colle ton Client ID Jamendo."));
            return;
        }
        settings.setJamendoClientId(id.trim());
        client = new JamendoClient(id.trim(), http);
        views.forEach(View::showReady);
        playFresh(mood);
    }

    // ================================================================== humeur

    /**
     * Met à jour l'humeur.
     *
     * @param immediate true = l'utilisateur vient de choisir explicitement une humeur :
     *                  si elle change de famille, on passe tout de suite à un morceau adapté.
     *                  false = changement progressif (glisser, voyage) : seule la file d'attente change.
     */
    public void setMood(Mood m, boolean immediate) {
        mood = m;
        if (!active || client == null) return;
        if (playlist != null && !immediate) return; // une playlist est en cours : on ne la remplace pas
        if (m.sector() == queueSector && playlist == null) return;
        if (immediate && playRequested) {
            debounce.stop();
            playFresh(m);
        } else {
            debounce.restart();
        }
    }

    /** Vide la file, cherche des morceaux pour cette humeur et lance le premier. */
    public void playFresh(Mood m) {
        mood = m;
        playlist = null;
        playRequested = true;
        if (!ensureKey()) return;
        refill(true);
    }

    /**
     * Joue une liste de morceaux précise (playlist proposée par le Recommandeur, favoris…).
     * Tant qu'elle n'est pas finie, les changements progressifs d'humeur ne la remplacent pas.
     * À la fin, on revient à la « radio d'humeur ».
     */
    public void playPlaylist(List<Morceau> morceaux, String nom) {
        List<Morceau> jouables = new ArrayList<>();
        for (Morceau m : morceaux) if (m.estJouable()) jouables.add(m);
        if (jouables.isEmpty()) {
            views.forEach(v -> v.showStatus("Aucun morceau écoutable dans « " + nom + " ».", true));
            return;
        }
        debounce.stop();
        generation++;            // annule une éventuelle recherche Jamendo en cours
        fetching = false;
        playlist = nom;
        queue.clear();
        queue.addAll(jouables);
        playRequested = true;
        playNext();
    }

    /** Cherche les derniers morceaux d'un artiste sur Jamendo et les joue. */
    public void playArtist(String artiste) {
        if (!ensureKey()) return;
        final JamendoClient c = client;
        final int gen = ++generation;
        views.forEach(v -> v.showStatus("Je cherche les morceaux de " + artiste + "…", false));
        io.submit(() -> {
            try {
                List<Morceau> found = c.morceauxDeLArtiste(artiste, 20);
                SwingUtilities.invokeLater(() -> {
                    if (gen != generation) return;
                    if (found.isEmpty()) views.forEach(v -> v.showStatus("Aucun morceau trouvé pour " + artiste + ".", true));
                    else playPlaylist(found, "Artiste : " + artiste);
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> views.forEach(v -> v.showStatus("Impossible de joindre Jamendo : " + e.getMessage(), true)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Le mode Écouter devient visible (ou non).
     *
     * @param autoplay démarrer la lecture tout de suite (la musique jouait dans l'autre mode)
     */
    public void setActive(boolean a, boolean autoplay) {
        active = a;
        if (!a) {
            debounce.stop();
            pause();
            return;
        }
        if (!ensureKey()) return;
        boolean stale = mood.sector() != queueSector;
        if (autoplay) {
            if (current != null && (!stale || playlist != null)) resume(); else playFresh(mood);
        } else if (stale || queue.isEmpty()) {
            refill(false); // on prépare la file sans lancer la lecture
        }
    }

    // ================================================================== commandes du lecteur

    public void toggle() {
        if (!ensureKey()) return;
        if (current == null) {
            playRequested = true;
            if (queue.isEmpty()) refill(true); else playNext();
        } else if (player.isPaused()) {
            playRequested = true;
            player.resume();
            notifyPlaying();
        } else {
            pause();
        }
    }

    public void pause() {
        player.pause();
        notifyPlaying();
    }

    public void resume() {
        if (current == null) {
            toggle();
            return;
        }
        playRequested = true;
        player.resume();
        notifyPlaying();
    }

    public void next() {
        if (!ensureKey()) return;
        playRequested = true;
        playNext();
    }

    /** Joue un morceau précis de la file d'attente. */
    public void play(Morceau t) {
        queue.remove(t);
        playRequested = true;
        start(t);
        publishQueue();
    }

    public void seekFraction(double f) {
        if (current == null || current.dureeSecondes() <= 0) return;
        player.seek(f * current.dureeSecondes());
    }

    public void setVolume(float v) {
        player.setVolume(v);
    }

    public void shutdown() {
        player.stop();
        io.shutdownNow();
    }

    // ================================================================== logique interne

    private boolean ensureKey() {
        if (client != null) return true;
        views.forEach(v -> v.showNeedsKey(null));
        return false;
    }

    private void playNext() {
        Morceau t = queue.poll();
        if (t == null) {
            if (playlist != null) {   // fin de la playlist : retour à la radio d'humeur
                String fini = playlist;
                playlist = null;
                views.forEach(v -> v.showStatus("Fin de « " + fini + " », retour à la radio d'humeur.", false));
                queueSector = null;
            }
            if (!fetching) refill(true);
            return;
        }
        start(t);
        publishQueue();
        if (queue.size() < LOW_WATER && playlist == null) fetchMore();
    }

    private void start(Morceau t) {
        current = t;
        played.add(t.id());
        player.play(t);
        views.forEach(v -> v.showNowPlaying(t));
        views.forEach(v -> v.showStatus("Chargement de « " + t.titre() + " »…", false));
        notifyPlaying();
    }

    /**
     * Nouvelle recherche pour l'humeur courante.
     *
     * @param startNow lancer le premier morceau dès réception
     */
    private void refill(boolean startNow) {
        if (client == null) return;
        Sector sector = mood.sector();
        if (sector != queueSector) {
            queueSector = sector;
            page = rnd.nextInt(3);          // un peu de hasard pour ne pas toujours entendre les mêmes
        }
        fetch(true, startNow);
    }

    private void fetchMore() {
        page++;
        fetch(false, false);
    }

    private void fetch(boolean replace, boolean startNow) {
        final int gen = ++generation;
        final Mood m = mood;
        final int offset = page * PAGE;
        final JamendoClient c = client;
        fetching = true;
        views.forEach(v -> v.showStatus("Je cherche des morceaux « " + m.sector().label().toLowerCase(Locale.FRANCE) + " »…", false));
        io.submit(() -> {
            List<Morceau> found;
            String error = null;
            try {
                found = c.morceauxPourHumeur(m, offset, PAGE);
                if (found.isEmpty() && offset > 0) found = c.morceauxPourHumeur(m, 0, PAGE); // plus de résultats : on repart du début
            } catch (IOException e) {
                found = List.of();
                error = e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            final List<Morceau> result = found;
            final String err = error;
            SwingUtilities.invokeLater(() -> onFetched(gen, result, err, replace, startNow));
        });
    }

    private void onFetched(int gen, List<Morceau> found, String error, boolean replace, boolean startNow) {
        if (gen != generation) return;   // une recherche plus récente a été lancée entre-temps
        fetching = false;
        if (error != null) {
            boolean keyProblem = error.toLowerCase(Locale.ROOT).contains("client");
            if (keyProblem) {
                client = null;
                views.forEach(v -> v.showNeedsKey("Jamendo refuse ce Client ID (" + error + "). Vérifie-le."));
            } else {
                views.forEach(v -> v.showStatus("Impossible de joindre Jamendo : " + error, true));
            }
            return;
        }
        List<Morceau> fresh = new ArrayList<>();
        for (Morceau t : found) if (!played.contains(t.id())) fresh.add(t);
        if (fresh.isEmpty()) fresh.addAll(found); // tout a déjà été écouté : tant pis, on réécoute
        Collections.shuffle(fresh, rnd);

        if (replace) queue.clear();
        queue.addAll(fresh);
        publishQueue();

        if (queue.isEmpty()) {
            views.forEach(v -> v.showStatus("Aucun morceau trouvé pour cette humeur, essaie une humeur voisine.", true));
            return;
        }
        views.forEach(v -> v.showStatus(" ", false));
        if (playRequested && (startNow || current == null)) playNext();
    }

    private void publishQueue() {
        List<Morceau> up = List.copyOf(queue);
        views.forEach(v -> v.showQueue(up));
    }

    private void notifyPlaying() {
        boolean p = isPlaying();
        views.forEach(v -> v.showPlaying(p));
    }

    // ================================================================== évènements du lecteur (thread audio)

    @Override
    public void onStarted(Morceau track) {
        SwingUtilities.invokeLater(() -> {
            if (track != current) return;
            consecutiveErrors = 0;
            views.forEach(v -> v.showStatus(" ", false));
            notifyPlaying();
        });
    }

    @Override
    public void onProgress(Morceau track, double seconds) {
        SwingUtilities.invokeLater(() -> {
            if (track == current) views.forEach(v -> v.showProgress(track, seconds));
        });
    }

    @Override
    public void onSpectrum(float[] bands) {
        SwingUtilities.invokeLater(() -> views.forEach(v -> v.showSpectrum(bands)));
    }

    @Override
    public void onFinished(Morceau track) {
        SwingUtilities.invokeLater(() -> {
            if (track == current && playRequested) playNext();
        });
    }

    @Override
    public void onError(Morceau track, String message) {
        SwingUtilities.invokeLater(() -> {
            if (track != current) return;
            consecutiveErrors++;
            if (consecutiveErrors >= 3) {
                current = null;
                notifyPlaying();
                views.forEach(v -> v.showStatus("Lecture impossible (" + message + "). Vérifie ta connexion.", true));
                return;
            }
            views.forEach(v -> v.showStatus("« " + track.titre() + " » illisible (" + message + "), morceau suivant…", true));
            playNext();
        });
    }
}
