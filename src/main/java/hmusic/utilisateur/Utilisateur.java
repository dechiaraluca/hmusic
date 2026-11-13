package hmusic.utilisateur;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Un utilisateur de hmusic.
 * <p>
 * Pourquoi cette classe est-elle {@code abstract} ?
 * Parce qu'un utilisateur « tout court » n'existe pas dans l'application : c'est toujours soit un
 * {@link Auditeur}, soit un {@link Admin}. La classe regroupe ce qu'ils ont en commun (identifiant,
 * mot de passe, date d'inscription, options de menu communes) et oblige chaque sous-classe à définir
 * ce qui lui est propre ({@link #role()}, {@link #optionsSpecifiques()}).
 * Si on pouvait écrire {@code new Utilisateur()}, on obtiendrait un objet sans rôle ni menu :
 * le compilateur l'interdit, ce qui évite ce cas incohérent.
 */
public abstract class Utilisateur implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String identifiant;
    private final String sel;
    private final String empreinteMotDePasse;
    private final LocalDateTime inscription;

    protected Utilisateur(String identifiant, String motDePasse) {
        this.identifiant = identifiant;
        this.sel = MotDePasse.nouveauSel();
        this.empreinteMotDePasse = MotDePasse.hacher(motDePasse, sel);
        this.inscription = LocalDateTime.now().withNano(0);
    }

    public String identifiant() {
        return identifiant;
    }

    public LocalDateTime inscription() {
        return inscription;
    }

    public boolean verifierMotDePasse(String motDePasse) {
        return motDePasse != null && MotDePasse.verifier(motDePasse, sel, empreinteMotDePasse);
    }

    /** Nom du rôle, affiché dans l'interface. */
    public abstract String role();

    /** Les options propres à ce type d'utilisateur. */
    protected abstract List<OptionMenu> optionsSpecifiques();

    /**
     * Le menu complet : les options propres au type d'utilisateur, puis les options communes.
     * <p>
     * C'est ici que joue le polymorphisme : l'interface appelle {@code utilisateur.menu()} sans savoir
     * si c'est un Auditeur ou un Admin, et Java exécute la bonne version de {@link #optionsSpecifiques()}.
     */
    public final List<OptionMenu> menu() {
        List<OptionMenu> options = new ArrayList<>(optionsSpecifiques());
        options.add(OptionMenu.JOURNAL);
        options.add(OptionMenu.BILAN);
        options.add(OptionMenu.DECONNEXION);
        return options;
    }

    @Override
    public String toString() {
        return identifiant + " (" + role() + ")";
    }
}
