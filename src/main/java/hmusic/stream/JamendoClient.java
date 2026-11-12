package hmusic.stream;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
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
 * classés par tags d'ambiance : parfait pour une écoute « selon l'humeur ».
 */
public class JamendoClient {

    private static final String API = "https://api.jamendo.com/v3.0/tracks/";

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

    /**
     * Cherche des morceaux correspondant à une humeur.
     *
     * @param offset décalage dans les résultats (pour obtenir d'autres morceaux)
     */
    public List<Track> tracksFor(Mood mood, int offset, int limit) throws IOException, InterruptedException {
        String url = API + "?client_id=" + enc(clientId)
                + "&format=json"
                + "&limit=" + limit
                + "&offset=" + offset
                + "&fuzzytags=" + MoodTags.tags(mood.sector())
                + "&speed=" + MoodTags.speed(mood)
                + "&durationbetween=90_480"          // ni jingles ni mix d'une heure
                + "&groupby=artist_id"               // un morceau par artiste : plus de variété
                + "&include=musicinfo"
                + "&audioformat=mp32"                // MP3 VBR, bonne qualité
                + "&imagesize=300";
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

        List<Track> tracks = new ArrayList<>();
        if (r.results == null) return tracks;
        for (Result x : r.results) {
            if (x.audio == null || x.audio.isBlank() || x.id == null) continue;
            Set<String> tags = new LinkedHashSet<>();
            if (x.musicinfo != null && x.musicinfo.tags != null) {
                if (x.musicinfo.tags.vartags != null) tags.addAll(x.musicinfo.tags.vartags);
                if (x.musicinfo.tags.genres != null) tags.addAll(x.musicinfo.tags.genres);
            }
            String image = x.image != null && !x.image.isBlank() ? x.image : x.album_image;
            tracks.add(new Track(x.id, nz(x.name), nz(x.artist_name), nz(x.album_name), x.duration,
                    x.audio, image, x.shareurl, x.license_ccurl, List.copyOf(tags)));
        }
        return tracks;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
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
        String license_ccurl;
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
