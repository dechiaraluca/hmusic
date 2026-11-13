package hmusic.reco;

import hmusic.catalogue.Catalogue;
import hmusic.catalogue.Morceau;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Recommandeur qui tient compte des goûts appris de l'auditeur.
 * <p>
 * <b>Patron Décorateur</b> : il implémente {@link Recommandeur} et <i>enveloppe</i> un autre
 * Recommandeur (le Mock ou l'IA). Il lui demande deux fois plus de morceaux que nécessaire, puis
 * remonte ceux qui correspondent aux goûts de l'auditeur. On ajoute ainsi une fonctionnalité
 * <b>sans modifier une seule ligne</b> de {@link MockRecommandeur} ni de {@link IaRecommandeur},
 * et ça marche avec n'importe quel Recommandeur, même un futur.
 * <p>
 * La promesse de l'interface est conservée : il ne fait que réordonner ce que l'autre recommandeur
 * a proposé, donc tous les morceaux viennent toujours du catalogue.
 */
public class RecommandeurPersonnalise implements Recommandeur {

    /**
     * Ce que « coûte » une place dans l'ordre d'origine. Avec 0,5, un morceau doit avoir 2 points de
     * goût de plus pour passer devant le morceau juste au-dessus : l'avis du recommandeur de base compte encore.
     */
    static final double POIDS_RANG = 0.5;

    private final Recommandeur base;
    private final ProfilGouts gouts;

    public RecommandeurPersonnalise(Recommandeur base, ProfilGouts gouts) {
        this.base = base;
        this.gouts = gouts;
    }

    @Override
    public Playlist recommander(String humeur, Catalogue catalogue, int taille) throws RecommandationException {
        Playlist p = base.recommander(humeur, catalogue, taille * 2);
        List<Morceau> choix = reordonner(p.morceaux(), gouts);
        choix = choix.subList(0, Math.min(taille, choix.size()));
        String source = gouts.estVide() ? p.source() : p.source() + ", ajustée à tes goûts";
        return new Playlist(humeur, choix, p.rejetes(), source);
    }

    /**
     * Note de chaque morceau = score de goût − rang d'origine × {@value #POIDS_RANG}, puis tri décroissant.
     * Sans goûts appris, l'ordre d'origine est inchangé (tri stable).
     */
    public static List<Morceau> reordonner(List<Morceau> proposes, ProfilGouts gouts) {
        List<Integer> rangs = new ArrayList<>();
        for (int i = 0; i < proposes.size(); i++) rangs.add(i);
        rangs.sort(Comparator.comparingDouble((Integer i) -> gouts.score(proposes.get(i)) - i * POIDS_RANG).reversed());
        List<Morceau> resultat = new ArrayList<>();
        for (int i : rangs) resultat.add(proposes.get(i));
        return resultat;
    }

    @Override
    public String nom() {
        return base.nom();
    }
}
