package hmusic.reco;

/** Le recommandeur n'a pas pu proposer de playlist (catalogue vide, IA injoignable, réponse inutilisable…). */
public class RecommandationException extends Exception {

    public RecommandationException(String message) {
        super(message);
    }

    public RecommandationException(String message, Throwable cause) {
        super(message, cause);
    }
}
