package hmusic.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

/**
 * Vérifie s'il existe une version plus récente de hmusic dans les Releases GitHub.
 * La version de l'application vient du pom.xml (écrite dans version.properties à la compilation),
 * celle de GitHub vient du tag de la dernière release (ex. « v2.4 »).
 */
public final class MiseAJour {

    private static final String DEPOT = "dechiaraluca/hmusic";
    private static final String API_DERNIERE = "https://api.github.com/repos/" + DEPOT + "/releases/latest";
    public static final String PAGE_TELECHARGEMENT = "https://github.com/" + DEPOT + "/releases/latest";

    /** Une release plus récente que celle qui tourne. */
    public record Nouveaute(String version, String page) {}

    private MiseAJour() {}

    /** Version de l'application en cours, ou « dev » si on la lance sans passer par Maven. */
    public static String versionActuelle() {
        try (InputStream in = MiseAJour.class.getResourceAsStream("/hmusic/version.properties")) {
            if (in == null) return "dev";
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty("version", "dev");
            return v.startsWith("${") ? "dev" : v;
        } catch (Exception e) {
            return "dev";
        }
    }

    /**
     * Interroge GitHub. Ne lève jamais d'exception : sans internet, dépôt privé ou réponse bizarre,
     * on renvoie simplement « rien de nouveau ».
     */
    public static Optional<Nouveaute> verifier() {
        String actuelle = versionActuelle();
        if (actuelle.equals("dev")) return Optional.empty();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(API_DERNIERE))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "hmusic")
                    .GET().build();
            HttpResponse<String> rep = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (rep.statusCode() != 200) return Optional.empty();

            JsonObject json = JsonParser.parseString(rep.body()).getAsJsonObject();
            String tag = json.get("tag_name").getAsString();
            String page = json.has("html_url") ? json.get("html_url").getAsString() : PAGE_TELECHARGEMENT;
            String distante = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
            return estPlusRecente(distante, actuelle) ? Optional.of(new Nouveaute(distante, page)) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Compare deux numéros de version morceau par morceau : « 2.10 » est plus récente que « 2.9 ».
     * Un morceau non numérique (ex. « 2.4-beta ») compte pour sa partie chiffrée.
     */
    public static boolean estPlusRecente(String candidate, String reference) {
        String[] a = candidate.split("\\.");
        String[] b = reference.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? nombre(a[i]) : 0;
            int y = i < b.length ? nombre(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int nombre(String morceau) {
        String chiffres = morceau.replaceAll("^(\\d*).*$", "$1");
        return chiffres.isEmpty() ? 0 : Integer.parseInt(chiffres);
    }
}
