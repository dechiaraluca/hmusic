package hmusic.ia;

// Repris du Projet 3 (seule la ligne « package » a été ajoutée).
// Le contrat de tout client d'IA : on lui pose une question, il renvoie la réponse.
// IaRecommandeur ne sait pas s'il parle à la vraie API ou à un faux client : il ne connaît que cette interface.
public interface AiClient {

    // Lance une AiException (avec un message lisible) en cas de problème
    String ask(String question);

    // Oublie la conversation en cours
    void reset();
}
