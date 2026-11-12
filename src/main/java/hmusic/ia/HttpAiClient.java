package hmusic.ia;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// Repris du Projet 3. Changements pour hmusic :
//  - ligne « package » ;
//  - second constructeur qui permet de choisir le message système (ici : programmateur musical
//    au lieu de tuteur Java) ;
//  - délai de réponse porté à 60 s (le prompt contient tout le catalogue).
// Vrai client : envoie la conversation à l'API Groq et renvoie la réponse de l'IA
public class HttpAiClient implements AiClient {

    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";
    // Le sujet indiquait llama-3.1-8b-instant, mais Groq l'a arrêté le 2026-08-16 (erreur 404).
    // openai/gpt-oss-20b est son remplaçant officiel. La variable AI_MODEL permet d'en choisir un autre.
    private static final String MODELE_PAR_DEFAUT = "openai/gpt-oss-20b";
    private static final String MESSAGE_SYSTEME =
            "Tu es un tuteur Java bienveillant pour un étudiant débutant. "
            + "Tu expliques simplement, avec un petit exemple, sans donner la solution complète "
            + "d'un exercice. Tu réponds en français.";
    private static final int ESSAIS_MAX = 3;

    private final String cleApi;
    private final String modele;
    private final String messageSysteme;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // L'historique : tous les messages échangés, renvoyés à chaque requête
    // pour que l'IA se souvienne du contexte
    private JsonArray historique = new JsonArray();

    public HttpAiClient(String cleApi) {
        this(cleApi, MESSAGE_SYSTEME);
    }

    public HttpAiClient(String cleApi, String messageSysteme) {
        this.cleApi = cleApi;
        this.messageSysteme = messageSysteme;
        String modeleChoisi = System.getenv("AI_MODEL");
        this.modele = (modeleChoisi == null || modeleChoisi.isBlank()) ? MODELE_PAR_DEFAUT : modeleChoisi.trim();
        reset();
    }

    @Override
    public void reset() {
        historique = new JsonArray();
        historique.add(message("system", messageSysteme));
    }

    @Override
    public String ask(String question) {
        historique.add(message("user", question));
        try {
            String reponse = envoyer();
            historique.add(message("assistant", reponse));
            return reponse;
        } catch (AiException e) {
            // La question n'a pas eu de réponse : on la retire pour garder un historique cohérent
            historique.remove(historique.size() - 1);
            throw e;
        }
    }

    private String envoyer() {
        // Le corps JSON est construit avec Gson : les guillemets dans la question ne cassent rien
        JsonObject corps = new JsonObject();
        corps.addProperty("model", modele);
        corps.add("messages", historique);

        HttpRequest requete = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + cleApi)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corps.toString()))
                .build();

        for (int essai = 1; essai <= ESSAIS_MAX; essai++) {
            HttpResponse<String> reponse = executer(requete);
            int statut = reponse.statusCode();

            if (statut == 200) {
                return extraireTexte(reponse.body());
            }
            if (statut == 429 && essai < ESSAIS_MAX) {
                // Trop de requêtes : on attend un peu plus à chaque essai, puis on réessaie
                attendre(2 * essai);
                continue;
            }
            throw new AiException(messagePourStatut(statut) + detailErreur(reponse.body()));
        }
        throw new AiException(messagePourStatut(429));
    }

    private HttpResponse<String> executer(HttpRequest requete) {
        try {
            return http.send(requete, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AiException("Impossible de joindre l'API. Vérifie ta connexion internet.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("Requête interrompue.");
        }
    }

    // Le texte se trouve dans choices[0].message.content
    private String extraireTexte(String json) {
        try {
            JsonObject racine = JsonParser.parseString(json).getAsJsonObject();
            return racine.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        } catch (RuntimeException e) {
            throw new AiException("Réponse de l'API illisible.");
        }
    }

    // En cas d'erreur, l'API renvoie souvent {"error": {"message": "..."}} : on l'ajoute au message
    private String detailErreur(String json) {
        try {
            String detail = JsonParser.parseString(json).getAsJsonObject()
                    .getAsJsonObject("error")
                    .get("message").getAsString();
            return "\nDétail de l'API : " + detail;
        } catch (RuntimeException e) {
            return ""; // pas de détail lisible
        }
    }

    private String messagePourStatut(int statut) {
        if (statut == 404) {
            return "Modèle « " + modele + " » ou adresse introuvable (404).";
        }
        if (statut == 401) {
            return "Clé API refusée (401). Vérifie la variable AI_API_KEY.";
        }
        if (statut == 429) {
            return "Trop de requêtes (429). Attends un peu avant de reposer une question.";
        }
        return "L'API a répondu avec le statut HTTP " + statut + ".";
    }

    private void attendre(int secondes) {
        try {
            Thread.sleep(secondes * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static JsonObject message(String role, String contenu) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", contenu);
        return message;
    }
}
