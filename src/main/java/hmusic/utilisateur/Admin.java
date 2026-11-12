package hmusic.utilisateur;

import java.io.Serial;
import java.util.List;

/** Un administrateur : il gère le catalogue (ajout, suppression, import depuis Jamendo). */
public class Admin extends Utilisateur {

    @Serial
    private static final long serialVersionUID = 1L;

    public Admin(String identifiant, String motDePasse) {
        super(identifiant, motDePasse);
    }

    @Override
    public String role() {
        return "Administrateur";
    }

    @Override
    protected List<OptionMenu> optionsSpecifiques() {
        return List.of(OptionMenu.AJOUTER_MORCEAU, OptionMenu.GERER_CATALOGUE, OptionMenu.IMPORTER_CATALOGUE,
                OptionMenu.LISTER_UTILISATEURS);
    }
}
