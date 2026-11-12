package hmusic.utilisateur;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hachage des mots de passe : SHA-256 avec un sel aléatoire.
 * <ul>
 *   <li>On ne stocke JAMAIS le mot de passe, seulement son empreinte (hash).</li>
 *   <li>Le sel (16 octets aléatoires, différent pour chaque utilisateur) empêche deux mots de passe
 *       identiques d'avoir la même empreinte, et rend inutiles les tables d'empreintes précalculées.</li>
 * </ul>
 * (En production, on utiliserait un algorithme volontairement lent comme bcrypt ou Argon2.)
 */
public final class MotDePasse {

    private static final SecureRandom RANDOM = new SecureRandom();

    private MotDePasse() {}

    public static String nouveauSel() {
        byte[] sel = new byte[16];
        RANDOM.nextBytes(sel);
        return Base64.getEncoder().encodeToString(sel);
    }

    public static String hacher(String motDePasse, String sel) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(Base64.getDecoder().decode(sel));
            byte[] empreinte = sha.digest(motDePasse.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(empreinte);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e); // impossible : SHA-256 est toujours présent en Java
        }
    }

    /** Comparaison en temps constant (évite de deviner l'empreinte en mesurant le temps de réponse). */
    public static boolean verifier(String motDePasse, String sel, String empreinteAttendue) {
        byte[] a = hacher(motDePasse, sel).getBytes(StandardCharsets.UTF_8);
        byte[] b = empreinteAttendue.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
