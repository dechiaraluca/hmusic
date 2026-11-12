package hmusic.stream;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import hmusic.catalogue.Morceau;
import hmusic.model.Mood;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client minimal de l'API Jamendo v3 (https://developer.jamendo.com/v3.0).
 * Jamendo propose des milliers de morceaux complets sous licence Creative Commons,
 * classés par genre et par tags d'ambiance.
 */
public class JamendoClient {

    private static final String API = "https://api.jamendo.com/v3.0/tracks/";
    /** Paramètres communs : JSON, infos musicales (genres, tags), MP3 de bonne qualité, pochettes 300 px. */
    private static final String COMMUN = "&format=json&include=musicinfo&audioformat=mp32&imagesize=300"
            + "&durationbetween=90_480"; // ni jingles ni mix d'une heure

    private final String clientId;
    private final HttpClient http;
    private final Gson gson = new Gson();

    public JamendoClient(String clientId, HttpClient http) {
        this.clientId = clientId;
        this.http = http;
    }

    /** Erreur renvoyée par Jamendo (clé invalide, quota dépassé…) ou par le réseau. */
    public static class JamendoException extends IOException {
        public JamendoException(String message) { super(message); }
    }

    /** Morceaux correspondant à une humeur (tags d'ambiance + tempo). */
    public List<Morceau> morceauxPourHumeur(Mood mood, int offset, int limit) throws IOException, InterruptedException {
        return requete("&limit=" + limit + "&offset=" + offset
                + "&fuzzytags=" + MoodTags.tags(mood.sector())
                + "&speed=" + MoodTags.speed(mood)
                + "&groupby=artist_id", null);   // un morceau par artiste : plus de variété
    }

    /** Morceaux populaires d'un genre (utilisé pour importer le catalogue). */
    public List<Morceau> morceauxDuGenre(String genre, int limit) throws IOException, InterruptedException {
        return requete("&limit=" + limit + "&tags=" + enc(genre) + "&order=popularity_total&groupby=artist_id", genre);
    }

    /** Derniers morceaux d'un artiste (utilisé pour les abonnements). */
    public List<Morceau> morceauxDeLArtiste(String artiste, int limit) throws IOException, InterruptedException {
        return requete("&limit=" + limit + "&artist_name=" + enc(artiste) + "&order=releasedate_desc", null);
    }

    /**
     * @param genreForce genre à attribuer aux morceaux (null = premier genre indiqué par Jamendo)
     */
    private List<Morceau> requete(String parametres, String genreForce) throws IOException, InterruptedException {
        String url = API + "?client_id=" + enc(clientId) + COMMUN + parametres;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) throw new JamendoException("Jamendo a répondu HTTP " + resp.statusCode());

        Response r;
        try {
            r = gson.fromJson(resp.body(), Response.class);
        } catch (JsonParseException e) {
            throw new JamendoException("Réponse Jamendo illisible");
        }
        if (r == null || r.headers == null) throw new JamendoException("Réponse Jamendo vide");
        if (!"success".equalsIgnoreCase(r.headers.status)) {
            String msg = r.headers.error_message == null || r.headers.error_message.isBlank()
                    ? "erreur " + r.headers.code : r.headers.error_message;
            throw new JamendoException("Jamendo : " + msg);
        }

        List<Morceau> morceaux = new ArrayList<>();
        if (r.results == null) return morceaux;
        for (Result x : r.results) {
            if (x.audio == null || x.audio.isBlank() || x.id == null || x.name == null || x.artist_name == null) continue;
            Set<String> tags = new LinkedHashSet<>();
            String genre = genreForce;
            if (x.musicinfo != null && x.musicinfo.tags != null) {
                if (x.musicinfo.tags.vartags != null) tags.addAll(x.musicinfo.tags.vartags);
                if (x.musicinfo.tags.genres != null) {
                    tags.addAll(x.musicinfo.tags.genres);
                    if (genre == null && !x.musicinfo.tags.genres.isEmpty()) genre = x.musicinfo.tags.genres.get(0);
                }
            }
            String image = x.image != null && !x.image.isBlank() ? x.image : x.album_image;
            try {
                morceaux.add(new Morceau("jamendo-" + x.id, x.name, x.artist_name, genre, x.album_name, x.duration,
                        x.audio, image, x.shareurl, List.copyOf(tags)));
            } catch (IllegalArgumentException ignored) {
                // morceau incomplet (titre ou artiste vide) : on l'ignore
            }
        }
        return morceaux;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // --- structure du JSON renvoyé (remplie automatiquement par Gson) ---

    private static final class Response {
        Headers headers;
        List<Result> results;
    }

    private static final class Headers {
        String status;
        int code;
        String error_message;
    }

    private static final class Result {
        String id;
        String name;
        int duration;
        String artist_name;
        String album_name;
        String image;
        String album_image;
        String audio;
        String shareurl;
        MusicInfo musicinfo;
    }

    private static final class MusicInfo {
        TagGroups tags;
    }

    private static final class TagGroups {
        List<String> genres;
        List<String> vartags;
    }
}
