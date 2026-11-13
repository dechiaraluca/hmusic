package hmusic.ui;

import java.awt.event.KeyEvent;
import java.awt.im.InputContext;
import java.util.Locale;

/**
 * Le « clavier musical » du mode Composer : la rangée du milieu du clavier joue les degrés de la gamme.
 * <pre>
 *  AZERTY :  Q  S  D  F  G  H  J  K  L  M
 *  QWERTY :  A  S  D  F  G  H  J  K  L  ;
 *  degré  :  0  1  2  3  4  5  6  7  8  9     (0 = tonique, 7 = tonique une octave plus haut)
 * </pre>
 * Les deux dispositions sont acceptées en même temps : sur un clavier AZERTY, la touche A est en
 * haut à gauche, assez loin pour ne pas gêner.
 */
public final class ClavierMusical {

    public static final int NB_TOUCHES = 10;

    private ClavierMusical() {}

    /** @return le degré joué par cette touche, ou -1 si elle ne fait pas partie du clavier musical */
    public static int degre(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_Q, KeyEvent.VK_A -> 0;
            case KeyEvent.VK_S -> 1;
            case KeyEvent.VK_D -> 2;
            case KeyEvent.VK_F -> 3;
            case KeyEvent.VK_G -> 4;
            case KeyEvent.VK_H -> 5;
            case KeyEvent.VK_J -> 6;
            case KeyEvent.VK_K -> 7;
            case KeyEvent.VK_L -> 8;
            case KeyEvent.VK_M, KeyEvent.VK_SEMICOLON -> 9;
            default -> -1;
        };
    }

    /** Les lettres à afficher, selon la disposition du clavier de l'ordinateur (AZERTY en France / Belgique). */
    public static String[] etiquettes() {
        Locale l = null;
        try {
            l = InputContext.getInstance().getLocale();
        } catch (RuntimeException ignored) {
            // pas d'information sur le clavier : on se fie à la langue du système
        }
        if (l == null) l = Locale.getDefault();
        boolean azerty = l.getLanguage().equals("fr") && !l.getCountry().equals("CA") && !l.getCountry().equals("CH");
        return azerty
                ? new String[]{"Q", "S", "D", "F", "G", "H", "J", "K", "L", "M"}
                : new String[]{"A", "S", "D", "F", "G", "H", "J", "K", "L", ";"};
    }
}
