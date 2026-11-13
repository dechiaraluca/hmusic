package hmusic.ui;

import hmusic.utilisateur.GestionUtilisateurs;
import hmusic.utilisateur.GestionUtilisateurs.CompteException;
import hmusic.utilisateur.Utilisateur;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;

/**
 * Écran de connexion et d'inscription, affiché au lancement et après une déconnexion.
 * Toute la logique (vérifications, hachage, sauvegarde) est dans {@link GestionUtilisateurs} :
 * cette classe ne fait qu'afficher et transmettre.
 */
public class ConnexionDialog extends JDialog {

    private final GestionUtilisateurs comptes;
    private final HintField identifiant = new HintField("Identifiant");
    private final HintPasswordField motDePasse = new HintPasswordField("Mot de passe");
    private final HintPasswordField confirmation = new HintPasswordField("Confirme le mot de passe");
    private final JLabel titre = Theme.label(" ", Font.BOLD, 26, Theme.TEXT);
    private final JLabel aide = Theme.label(" ", Font.PLAIN, 13, Theme.MUTED);
    private final JLabel erreur = Theme.label(" ", Font.PLAIN, 12.5f, Dialogues.ERREUR);
    private final JPanel boutons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private boolean inscription;
    private Utilisateur resultat;

    private ConnexionDialog(GestionUtilisateurs comptes) {
        // (Window) null : fenêtre sans propriétaire, elle a donc son propre bouton dans la barre des tâches
        super((Window) null, "hmusic — connexion", ModalityType.APPLICATION_MODAL);
        this.comptes = comptes;

        JPanel box = new JPanel();
        box.setBackground(Theme.BG);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(30, 40, 30, 40));

        JLabel logo = new JLabel("hmusic", new ImageIcon(Theme.appIcon(44)), SwingConstants.LEFT);
        logo.setFont(Theme.font(Font.BOLD, 24));
        logo.setForeground(Theme.TEXT);
        logo.setIconTextGap(12);
        boutons.setOpaque(false);

        for (JComponent c : new JComponent[]{identifiant, motDePasse, confirmation}) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        }
        for (JComponent c : new JComponent[]{logo, titre, aide, identifiant, motDePasse, confirmation, erreur, boutons}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
        }
        box.add(logo);
        box.add(Box.createVerticalStrut(26));
        box.add(titre);
        box.add(Box.createVerticalStrut(4));
        box.add(aide);
        box.add(Box.createVerticalStrut(18));
        box.add(identifiant);
        box.add(Box.createVerticalStrut(10));
        box.add(motDePasse);
        box.add(Box.createVerticalStrut(10));
        box.add(confirmation);
        box.add(Box.createVerticalStrut(10));
        box.add(erreur);
        box.add(Box.createVerticalStrut(10));
        box.add(boutons);

        identifiant.addActionListener(e -> motDePasse.requestFocusInWindow());
        motDePasse.addActionListener(e -> {
            if (inscription) confirmation.requestFocusInWindow(); else valider();
        });
        confirmation.addActionListener(e -> valider());

        setContentPane(box);
        setIconImages(List.of(Theme.appIcon(16), Theme.appIcon(32), Theme.appIcon(64)));
        afficherMode(comptes.aucunCompte()); // aucun compte : on commence par l'inscription
        setSize(460, 520);
        setResizable(false);
        setLocationRelativeTo(null);

        // Windows empêche souvent une nouvelle fenêtre de « voler » le premier plan (lancement depuis un
        // terminal, un .bat…) : elle s'ouvre alors derrière les autres. Astuce classique : la passer
        // « toujours au-dessus » le temps de l'ouverture, la ramener devant, puis rendre la main.
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                setAlwaysOnTop(true);
                toFront();
                requestFocus();
                identifiant.requestFocusInWindow();
                setAlwaysOnTop(false);
            }
        });
    }

    /**
     * Affiche l'écran et attend que l'utilisateur se connecte.
     *
     * @return l'utilisateur connecté, ou null si la fenêtre a été fermée
     */
    public static Utilisateur demander(GestionUtilisateurs comptes) {
        ConnexionDialog d = new ConnexionDialog(comptes);
        d.setVisible(true); // bloquant (fenêtre modale)
        return d.resultat;
    }

    private void afficherMode(boolean modeInscription) {
        inscription = modeInscription;
        erreur.setText(" ");
        confirmation.setVisible(modeInscription);
        titre.setText(modeInscription ? "Créer un compte" : "Connexion");
        aide.setText(modeInscription
                ? (comptes.aucunCompte() ? "Premier compte : il sera administrateur." : "Tu seras inscrit·e comme auditeur·rice.")
                : "Content de te revoir !");
        boutons.removeAll();
        boutons.add(new PillButton(modeInscription ? "S'inscrire" : "Se connecter", true, this::valider));
        boutons.add(Box.createHorizontalStrut(10));
        if (!comptes.aucunCompte()) {
            boutons.add(new PillButton(modeInscription ? "J'ai déjà un compte" : "Créer un compte", false,
                    () -> afficherMode(!inscription)));
        }
        boutons.revalidate();
        boutons.repaint();
        identifiant.requestFocusInWindow();
    }

    private void valider() {
        char[] mdp = motDePasse.getPassword();
        char[] conf = confirmation.getPassword();
        try {
            resultat = inscription
                    ? comptes.inscrire(identifiant.getText(), new String(mdp), new String(conf))
                    : comptes.connecter(identifiant.getText(), new String(mdp));
            dispose();
        } catch (CompteException e) {
            erreur.setText(e.getMessage());
        } finally {
            Arrays.fill(mdp, '\0');  // on efface le mot de passe de la mémoire dès qu'il ne sert plus
            Arrays.fill(conf, '\0');
        }
    }
}
