package hmusic.ui;

import hmusic.analysis.MoodAnalyzer;
import hmusic.catalogue.Catalogue;
import hmusic.data.MoodJournal;
import hmusic.data.Stockage;
import hmusic.reco.Playlist;
import hmusic.reco.Recommandeur;
import hmusic.stream.ImportJamendo;
import hmusic.stream.JamendoClient;
import hmusic.utilisateur.Auditeur;
import hmusic.utilisateur.GestionUtilisateurs;
import hmusic.utilisateur.OptionMenu;
import hmusic.utilisateur.Utilisateur;
import hmusic.engine.EngineListener;
import hmusic.engine.MoodEngine;
import hmusic.model.Journey;
import hmusic.model.Mood;
import hmusic.model.MusicalProfile;
import hmusic.model.Sector;
import hmusic.model.Station;
import hmusic.stream.ListenController;
import hmusic.catalogue.Morceau;
import hmusic.ui.ModeSwitch.Mode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Fenêtre principale : assemble les composants et fait le lien (contrôleur) entre
 * l'humeur choisie et les deux sources de musique :
 * <ul>
 *   <li>mode Composer : le compositeur génératif {@link MoodEngine}</li>
 *   <li>mode Écouter : de vrais morceaux Jamendo via {@link ListenController}</li>
 * </ul>
 */
public class MainWindow extends JFrame implements EngineListener, ListenController.View {

    private static final int CUSTOM_JOURNEY_SECONDS = 180;
    /** En mode Écouter, un voyage dure plus longtemps pour laisser passer plusieurs morceaux. */
    private static final int LISTEN_JOURNEY_FACTOR = 3;
    private static final int MEMORY_DOTS = 80;

    private final MoodEngine engine;
    private final ListenController listen;
    private final MoodJournal journal;
    private final MoodAnalyzer analyzer = new MoodAnalyzer();

    private final MoodPad pad;
    private final Visualizer viz = new Visualizer();
    private final ListenPanel listenPanel;
    private final CardLayout stageCards = new CardLayout();
    private final JPanel stage = new JPanel(stageCards);
    private final ModeSwitch modeSwitch;
    private final PlayerBar player;
    private final Sidebar sidebar;
    private final HintField input = new HintField("ex. : un peu fatigué mais content de ma journée");
    private final PillButton composeButton;
    private final JLabel feedback = Theme.label(" ", Font.PLAIN, 13, Theme.MUTED);
    private final JLabel moodName = Theme.label(" ", Font.BOLD, 24, Theme.TEXT);
    private final JLabel moodDetails = Theme.label(" ", Font.PLAIN, 13, Theme.MUTED);
    private final JPanel journeyPanel = new JPanel(new BorderLayout(10, 6));
    private final JLabel journeyLabel = Theme.label(" ", Font.BOLD, 13, Theme.TEXT);
    private final Slider journeyProgress = new Slider(0, null);
    private final Timer journeyTimer = new Timer(200, e -> journeyTick());

    private final Utilisateur utilisateur;
    private final GestionUtilisateurs comptes;
    private final Catalogue catalogue;
    private final Recommandeur recommandeur;
    private final Runnable surDeconnexion;

    private Mode mode = Mode.COMPOSE;
    private Mood mood = Mood.NEUTRAL;
    private Journey journey;
    private int journeySeconds;
    private long journeyStart;
    private long lastJourneyTick;

    /**
     * @param surDeconnexion appelé quand l'utilisateur se déconnecte (la fenêtre est déjà fermée)
     */
    public MainWindow(MoodEngine engine, ListenController listen, Utilisateur utilisateur, GestionUtilisateurs comptes,
                      Catalogue catalogue, Recommandeur recommandeur, Runnable surDeconnexion) {
        super("hmusic — " + utilisateur.identifiant());
        this.engine = engine;
        this.listen = listen;
        this.utilisateur = utilisateur;
        this.comptes = comptes;
        this.catalogue = catalogue;
        this.recommandeur = recommandeur;
        this.surDeconnexion = surDeconnexion;
        // un journal d'humeur par utilisateur
        this.journal = new MoodJournal(Stockage.DOSSIER.resolve("journal-" + utilisateur.identifiant().toLowerCase(Locale.ROOT) + ".csv"));

        pad = new MoodPad(new MoodPad.Listener() {
            @Override public void moodPicked(Mood m, boolean adjusting) { onPadPick(m, adjusting); }

            @Override public void destinationPicked(Mood target) {
                sidebar.clearSelection();
                startJourney(new Journey("Voyage personnalisé", "vers « " + target.sector().label() + " »",
                        mood, target, CUSTOM_JOURNEY_SECONDS));
            }
        });
        player = new PlayerBar(this::togglePlay, this::next, this::exportMidi, this::setVolume, this::seek);
        sidebar = new Sidebar(utilisateur, this::playStation, this::startJourney, this::choisirOption);
        composeButton = new PillButton("Lancer la musique", true, this::analyzeText);
        modeSwitch = new ModeSwitch(this::setMode);
        listenPanel = new ListenPanel(listen);
        // seul un Auditeur a des favoris et des abonnements : les boutons n'apparaissent que pour lui
        if (utilisateur instanceof Auditeur auditeur) listenPanel.setActionsAuditeur(actionsAuditeur(auditeur));
        input.addActionListener(e -> analyzeText());

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(sidebar, BorderLayout.WEST);
        root.add(buildMain(), BorderLayout.CENTER);
        root.add(player, BorderLayout.SOUTH);
        setContentPane(root);

        engine.addListener(this);
        engine.addListener(viz);
        listen.addView(this);

        setIconImages(List.of(Theme.appIcon(16), Theme.appIcon(32), Theme.appIcon(64)));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                comptes.sauvegarder();
                listen.shutdown();
                engine.shutdown();
            }
        });
        setMinimumSize(new Dimension(1120, 720));
        setSize(1320, 820);
        setLocationRelativeTo(null);

        // état initial
        pad.setMemories(journal.recentMoods(MEMORY_DOTS));
        applyMood(Mood.NEUTRAL, false);
        showTrack(engine.getTitle(), engine.getProfile());
        feedback.setText("Bienvenue " + utilisateur.identifiant() + " (" + utilisateur.role().toLowerCase(Locale.FRANCE)
                + ") ! Ton menu est en bas à gauche.");
        journal.dominantSince(LocalDateTime.now().minusDays(7)).ifPresent(s ->
                feedback.setText("Bienvenue " + utilisateur.identifiant() + " ! Ces derniers jours, ton humeur dominante était : "
                        + s.label().toLowerCase(Locale.FRANCE) + "."));
    }

    // ================================================================== menu de l'utilisateur

    /** Exécute l'option choisie dans le menu (les options proposées dépendent du type d'utilisateur). */
    private void choisirOption(OptionMenu option) {
        switch (option) {
            case MES_FAVORIS -> AuditeurDialogs.favoris(this, (Auditeur) utilisateur, this::jouerListe,
                    this::jouerArtiste, comptes::sauvegarder);
            case MES_ABONNEMENTS -> AuditeurDialogs.abonnements(this, (Auditeur) utilisateur, this::jouerArtiste,
                    comptes::sauvegarder);
            case AJOUTER_MORCEAU -> AdminDialogs.ajouterMorceau(this, catalogue, catalogue::sauvegarder);
            case GERER_CATALOGUE -> AdminDialogs.gererCatalogue(this, catalogue, catalogue::sauvegarder);
            case IMPORTER_CATALOGUE -> importerCatalogue();
            case JOURNAL -> openJournal();
            case DECONNEXION -> deconnecter();
        }
    }

    private ListenPanel.ActionsAuditeur actionsAuditeur(Auditeur a) {
        return new ListenPanel.ActionsAuditeur() {
            @Override public boolean estFavori(Morceau m) { return a.estFavori(m); }

            @Override public void basculerFavori(Morceau m) {
                boolean ajoute = a.basculerFavori(m);
                comptes.sauvegarder();
                feedback.setText(ajoute ? "« " + m.titre() + " » ajouté à tes favoris." : "« " + m.titre() + " » retiré de tes favoris.");
            }

            @Override public boolean estArtisteFavori(String artiste) { return a.estArtisteFavori(artiste); }

            @Override public void basculerArtisteFavori(String artiste) {
                boolean ajoute = a.basculerArtisteFavori(artiste);
                comptes.sauvegarder();
                feedback.setText(artiste + (ajoute ? " fait partie de tes artistes favoris." : " ne fait plus partie de tes artistes favoris."));
            }

            @Override public boolean estAbonne(String artiste) { return a.estAbonne(artiste); }

            @Override public void basculerAbonnement(String artiste) {
                boolean ajoute = a.basculerAbonnement(artiste);
                comptes.sauvegarder();
                feedback.setText(ajoute ? "Tu es abonné·e à " + artiste + " : retrouve ses morceaux dans « Mes abonnements »."
                        : "Tu n'es plus abonné·e à " + artiste + ".");
            }
        };
    }

    /** Joue une liste précise (favoris…) : passe en mode Écouter si besoin. */
    private void jouerListe(List<Morceau> morceaux, String nom) {
        allerEnModeEcouter();
        listen.playPlaylist(morceaux, nom);
        feedback.setText("Lecture : " + nom);
    }

    private void jouerArtiste(String artiste) {
        allerEnModeEcouter();
        listen.playArtist(artiste);
    }

    private void allerEnModeEcouter() {
        stopJourney();
        sidebar.clearSelection();
        if (!listening()) setMode(Mode.LISTEN);
    }

    private void importerCatalogue() {
        Optional<JamendoClient> client = listen.jamendo();
        if (client.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Connecte d'abord Jamendo : passe en mode Écouter et colle ton Client ID.",
                    "Jamendo non configuré", JOptionPane.INFORMATION_MESSAGE);
            setMode(Mode.LISTEN);
            return;
        }
        feedback.setText("Import du catalogue depuis Jamendo…");
        new SwingWorker<List<Morceau>, String>() {
            @Override protected List<Morceau> doInBackground() throws Exception {
                return ImportJamendo.telecharger(client.get(), 20, this::publish);
            }

            @Override protected void process(List<String> messages) {
                feedback.setText("Import du catalogue depuis Jamendo : " + messages.get(messages.size() - 1));
            }

            @Override protected void done() {
                try {
                    int ajoutes = 0;
                    for (Morceau m : get()) if (catalogue.ajouter(m)) ajoutes++; // ajout sur le thread Swing
                    catalogue.sauvegarder();
                    feedback.setText("Import terminé : " + ajoutes + " nouveau(x) morceau(x). Le catalogue en compte "
                            + catalogue.taille() + " dans " + catalogue.genres().size() + " genres.");
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    feedback.setText("Import impossible : " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void deconnecter() {
        stopJourney();
        engine.pause();
        listen.setActive(false, false);
        engine.removeListener(this);
        engine.removeListener(viz);
        listen.removeView(this);
        listen.removeView(listenPanel);
        comptes.sauvegarder();
        dispose();
        surDeconnexion.run();
    }

    // ================================================================== construction de l'UI

    private JComponent buildMain() {
        JPanel main = new JPanel(new BorderLayout(0, 20));
        main.setOpaque(false);
        main.setBorder(new EmptyBorder(24, 28, 20, 28));
        main.add(buildHeader(), BorderLayout.NORTH);

        stage.setOpaque(false);
        stage.add(viz, Mode.COMPOSE.name());
        stage.add(listenPanel, Mode.LISTEN.name());

        JPanel body = new JPanel(new BorderLayout(22, 0));
        body.setOpaque(false);
        body.add(buildMoodColumn(), BorderLayout.WEST);
        body.add(stage, BorderLayout.CENTER);
        main.add(body, BorderLayout.CENTER);
        return main;
    }

    private JComponent buildHeader() {
        JLabel greet = Theme.label(greeting(), Font.PLAIN, 14, Theme.MUTED);
        JLabel question = Theme.label("Comment tu te sens ?", Font.BOLD, 30, Theme.TEXT);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        greet.setAlignmentX(LEFT_ALIGNMENT);
        question.setAlignmentX(LEFT_ALIGNMENT);
        titles.add(greet);
        titles.add(Box.createVerticalStrut(2));
        titles.add(question);

        JPanel switchBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        switchBox.setOpaque(false);
        switchBox.add(modeSwitch);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(titles, BorderLayout.WEST);
        top.add(switchBox, BorderLayout.EAST);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.add(input, BorderLayout.CENTER);
        row.add(composeButton, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(780, 42));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{top, row, feedback}) c.setAlignmentX(LEFT_ALIGNMENT);
        header.add(top);
        header.add(Box.createVerticalStrut(12));
        header.add(row);
        header.add(Box.createVerticalStrut(10));
        header.add(feedback);
        return header;
    }

    private JComponent buildMoodColumn() {
        JPanel col = new JPanel(new BorderLayout(0, 14));
        col.setOpaque(false);
        col.setPreferredSize(new Dimension(410, 10));
        col.add(pad, BorderLayout.CENTER);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel hint = Theme.label("Clic : ton humeur  ·  Clic droit : une destination à atteindre en douceur",
                Font.PLAIN, 11.5f, Theme.MUTED);

        journeyPanel.setOpaque(false);
        journeyPanel.add(journeyLabel, BorderLayout.NORTH);
        journeyPanel.add(journeyProgress, BorderLayout.CENTER);
        journeyPanel.add(new PillButton("Arrêter", false, this::stopJourney), BorderLayout.EAST);
        journeyPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        journeyPanel.setVisible(false);

        for (JComponent c : new JComponent[]{moodName, moodDetails, hint, journeyPanel}) c.setAlignmentX(LEFT_ALIGNMENT);
        info.add(moodName);
        info.add(Box.createVerticalStrut(2));
        info.add(moodDetails);
        info.add(Box.createVerticalStrut(6));
        info.add(hint);
        info.add(Box.createVerticalStrut(10));
        info.add(journeyPanel);
        col.add(info, BorderLayout.SOUTH);
        return col;
    }

    private static String greeting() {
        int h = LocalTime.now().getHour();
        if (h < 5) return "Bonne nuit";
        if (h < 12) return "Bonjour";
        if (h < 18) return "Bon après-midi";
        return "Bonsoir";
    }

    // ================================================================== les deux modes

    private boolean listening() {
        return mode == Mode.LISTEN;
    }

    private void setMode(Mode m) {
        if (m == mode) return;
        boolean wasPlaying = listening() ? listen.isPlaying() : engine.isPlaying();
        mode = m;
        modeSwitch.setMode(m);
        stageCards.show(stage, m.name());
        player.setListenMode(listening());
        if (listening()) {
            engine.pause();
            refreshListenBar();
            listen.setActive(true, wasPlaying);
            feedback.setText("Mode Écouter : écris ton humeur et le " + recommandeur.nom()
                    + " te compose une playlist du catalogue (" + catalogue.taille() + " morceaux).");
        } else {
            listen.setActive(false, false);
            showTrack(engine.getTitle(), engine.getProfile());
            player.setPlaying(false);
            if (wasPlaying) engine.play();
        }
    }

    private void togglePlay() {
        if (listening()) listen.toggle(); else engine.toggle();
    }

    private void next() {
        if (listening()) listen.next(); else engine.requestNewTrack();
    }

    private void seek(double fraction) {
        if (listening()) listen.seekFraction(fraction);
    }

    private void setVolume(double v) {
        engine.setVolume((int) Math.round(v * 100));
        listen.setVolume((float) v);
    }

    // ================================================================== humeur

    /**
     * @param immediate l'utilisateur a choisi explicitement cette humeur
     *                  (en mode Écouter, on change alors de morceau tout de suite)
     */
    private void applyMood(Mood m, boolean immediate) {
        mood = m;
        engine.setMood(m);
        listen.setMood(m, immediate);
        pad.setMood(m);
        viz.setMood(m);
        listenPanel.setMood(m);
        Sector s = m.sector();
        Color c = m.color();
        moodName.setText(s.label());
        moodName.setForeground(Mood.mix(c, Color.WHITE, 0.25));
        moodDetails.setText(String.format(Locale.FRANCE, "%s  ·  valence %+.2f  ·  énergie %+.2f",
                s.description(), m.valence(), m.energy()));
        composeButton.setAccent(c);
        modeSwitch.setAccent(Mood.mix(c, Theme.ACCENT, 0.25));
        player.setAccent(c);
    }

    private void logMood(Mood m, String source) {
        journal.add(m, source);
        pad.setMemories(journal.recentMoods(MEMORY_DOTS));
    }

    private void onPadPick(Mood m, boolean adjusting) {
        if (journey != null) stopJourney();
        sidebar.clearSelection();
        applyMood(m, !adjusting);
        if (!listening()) engine.play();          // en mode Composer, la musique suit le glissement en direct
        if (!adjusting) {
            logMood(m, "pavé d'humeur");
            if (listening() && !listen.isPlaying()) listen.playFresh(m);
        }
    }

    private void playStation(Station s) {
        stopJourney();
        applyMood(s.mood(), false);
        logMood(s.mood(), "station « " + s.name() + " »");
        if (listening()) {
            listen.playFresh(s.mood());
        } else {
            engine.requestNewTrack();
            engine.play();
        }
        feedback.setText("Station « " + s.name() + " » : " + s.tagline().toLowerCase(Locale.FRANCE) + ".");
    }

    private void analyzeText() {
        String text = input.getText().trim();
        if (text.isEmpty()) {
            feedback.setText("Écris quelques mots sur ce que tu ressens, par exemple « stressé par les partiels ».");
            return;
        }
        if (listening()) {        // en mode Écouter : playlist proposée par le Recommandeur
            proposerPlaylist(text);
            return;
        }
        Optional<MoodAnalyzer.Analysis> result = analyzer.analyze(text);
        if (result.isEmpty()) {
            feedback.setText("Je n'ai pas reconnu d'émotion… essaie avec : stressé, fatigué, heureux, nostalgique, motivé, en colère…");
            return;
        }
        MoodAnalyzer.Analysis a = result.get();
        stopJourney();
        sidebar.clearSelection();
        applyMood(a.mood(), false);
        logMood(a.mood(), "« " + text + " »");
        if (listening()) {
            listen.playFresh(a.mood());
        } else {
            engine.requestNewTrack();
            engine.play();
        }
        feedback.setText("J'ai compris : " + a.mood().sector().label().toLowerCase(Locale.FRANCE)
                + "   (mots repérés : " + String.join(", ", a.words()) + ")");
    }

    // ================================================================== playlist du Recommandeur

    private static final int TAILLE_PLAYLIST = 12;

    /**
     * Demande une playlist au Recommandeur (IA ou hors ligne : cette méthode ne sait pas lequel,
     * elle ne connaît que l'interface {@link Recommandeur}).
     */
    private void proposerPlaylist(String humeur) {
        if (catalogue.estVide()) {
            feedback.setText("Le catalogue est vide : un administrateur doit d'abord l'importer (menu > Importer depuis Jamendo).");
            return;
        }
        stopJourney();
        sidebar.clearSelection();
        feedback.setText("Le " + recommandeur.nom() + " prépare ta playlist…");
        // copie du catalogue : le calcul se fait en arrière-plan pendant que l'interface reste utilisable
        Catalogue copie = new Catalogue();
        catalogue.tous().forEach(copie::ajouter);

        new SwingWorker<Playlist, Void>() {
            @Override protected Playlist doInBackground() throws Exception {
                return recommandeur.recommander(humeur, copie, TAILLE_PLAYLIST);
            }

            @Override protected void done() {
                Playlist p;
                try {
                    p = get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    feedback.setText("Pas de playlist : " + cause.getMessage());
                    return;
                }
                if (p.morceaux().isEmpty()) {
                    feedback.setText("Le " + p.source() + " n'a proposé aucun morceau du catalogue.");
                    return;
                }
                listen.playPlaylist(p.morceaux(), "Playlist « " + humeur + " »");
                analyzer.analyze(humeur).ifPresent(a -> applyMood(a.mood(), false)); // pour colorer l'interface
                logMood(mood, "playlist « " + humeur + " »");
                String msg = "Playlist proposée par le " + p.source() + " : " + p.morceaux().size() + " morceau(x).";
                if (!p.rejetes().isEmpty()) {
                    msg += "  " + p.rejetes().size() + " proposition(s) rejetée(s) car absente(s) du catalogue : "
                            + String.join(" ; ", p.rejetes());
                }
                feedback.setText(msg);
            }
        }.execute();
    }

    // ================================================================== voyages guidés

    private void startJourney(Journey j) {
        stopJourney();
        journey = j;
        journeySeconds = listening() ? j.seconds() * LISTEN_JOURNEY_FACTOR : j.seconds();
        journeyStart = System.currentTimeMillis();
        lastJourneyTick = journeyStart;
        applyMood(j.from(), false);
        pad.setTarget(j.to());
        logMood(j.from(), "début du voyage « " + j.name() + " »");
        if (listening()) {
            listen.playFresh(j.from());
        } else {
            engine.requestNewTrack();
            engine.play();
        }
        journeyProgress.setFill(j.to().color());
        journeyProgress.setValue(0);
        journeyLabel.setText("Voyage : " + j.name());
        journeyPanel.setVisible(true);
        journeyTimer.start();
        feedback.setText("Voyage « " + j.name() + " » (" + j.description() + ") : la musique rejoint d'abord ton humeur, "
                + "puis t'emmène doucement ailleurs.");
    }

    private void journeyTick() {
        if (journey == null) return;
        long now = System.currentTimeMillis();
        boolean playing = listening() ? listen.isPlaying() : engine.isPlaying();
        if (!playing) journeyStart += now - lastJourneyTick; // le voyage se met en pause avec la musique
        lastJourneyTick = now;

        double t = (now - journeyStart) / (journeySeconds * 1000.0);
        if (t >= 1) {
            Journey done = journey;
            applyMood(done.to(), false);
            logMood(done.to(), "fin du voyage « " + done.name() + " »");
            stopJourney();
            feedback.setText("Destination atteinte : " + done.to().sector().label().toLowerCase(Locale.FRANCE) + ".");
            return;
        }
        // principe d'iso-modulation : on reste d'abord sur l'humeur de départ (15 % du temps), puis on glisse
        double u = t < 0.15 ? 0 : (t - 0.15) / 0.85;
        double eased = u * u * (3 - 2 * u);
        applyMood(journey.from().lerp(journey.to(), eased), false);
        journeyProgress.setValue(t);
        long remaining = Math.round((1 - t) * journeySeconds);
        journeyLabel.setText(String.format("Voyage : %s  —  encore %d:%02d", journey.name(), remaining / 60, remaining % 60));
    }

    private void stopJourney() {
        journeyTimer.stop();
        journey = null;
        pad.setTarget(null);
        journeyPanel.setVisible(false);
    }

    // ================================================================== journal & export

    private void openJournal() {
        new JournalDialog(this, journal, () -> pad.setMemories(journal.recentMoods(MEMORY_DOTS))).setVisible(true);
    }

    private void exportMidi() {
        if (!engine.hasRecording()) {
            JOptionPane.showMessageDialog(this,
                    "Lance d'abord la musique : hmusic enregistre tout ce qu'il compose pendant la session.",
                    "Rien à exporter", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exporter la session en MIDI");
        fc.setFileFilter(new FileNameExtensionFilter("Fichier MIDI (*.mid)", "mid", "midi"));
        String name = engine.getTitle().replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        fc.setSelectedFile(new File((name.isEmpty() ? "hmusic-session" : name) + ".mid"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".mid")) f = new File(f.getPath() + ".mid");
        try {
            engine.exportMidi(f);
            feedback.setText("Session exportée dans " + f.getName() + " — ouvre-la dans MuseScore, GarageBand, FL Studio…");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Export impossible : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================================================================== évènements du compositeur (thread audio)

    @Override
    public void onTrackChanged(String title, MusicalProfile profile) {
        SwingUtilities.invokeLater(() -> showTrack(title, profile));
    }

    private void showTrack(String title, MusicalProfile p) {
        Mood m = engine.getMood();
        viz.setTrack(title, "Composé à l'instant pour toi · " + p.instruments().description());
        if (listening()) return;
        String sub = m.sector().label() + " · " + p.bpm() + " BPM · mode " + p.scale().label().toLowerCase(Locale.FRANCE);
        player.setTrack(title, sub, m);
    }

    @Override
    public void onStep(int step, int bar, long trackNanos) {
        if (step % 4 != 0 || listening()) return;
        double fraction = (bar * 16 + step) / (double) (MoodEngine.BARS_PER_TRACK * 16);
        String time = formatTime(trackNanos / 1e9);
        String barText = "mesure " + (bar + 1) + "/" + MoodEngine.BARS_PER_TRACK;
        SwingUtilities.invokeLater(() -> {
            if (!listening()) player.setProgress(fraction, time, barText);
        });
    }

    @Override
    public void onNote(int channel, int pitch, int velocity) {
        if (channel == MoodEngine.CH_DRUMS && pitch == 36) {
            SwingUtilities.invokeLater(() -> pad.pulse(velocity / 127f));
        }
    }

    @Override
    public void onPlayStateChanged(boolean playing) {
        SwingUtilities.invokeLater(() -> {
            viz.setPlaying(playing);
            if (!listening()) player.setPlaying(playing);
        });
    }

    // ================================================================== évènements du mode Écouter (thread Swing)

    private void refreshListenBar() {
        Morceau t = listen.current();
        if (t == null) {
            player.setTrack("Mode Écouter", "De vrais morceaux choisis selon ton humeur", mood);
            player.setProgress(0, "0:00", "—");
            player.setPlaying(false);
        } else {
            showNowPlaying(t);
            player.setPlaying(listen.isPlaying());
        }
    }

    @Override
    public void showNowPlaying(Morceau t) {
        if (!listening()) return;
        player.setTrack(t.titre(), t.artiste() + " · via Jamendo", mood);
        player.setProgress(0, "0:00", t.dureeTexte());
        ImageCache.load(t.urlImage(), img -> {
            if (img != null && listening() && listen.current() == t) player.setCoverImage(img);
        });
    }

    @Override
    public void showProgress(Morceau t, double seconds) {
        if (!listening()) return;
        double f = t.dureeSecondes() > 0 ? seconds / t.dureeSecondes() : 0;
        player.setProgress(f, formatTime(seconds), t.dureeTexte());
    }

    @Override
    public void showSpectrum(float[] bands) {
        // les basses font battre le pavé d'humeur, comme la grosse caisse en mode Composer
        if (bands.length > 10) {
            float bass = 0;
            for (int i = 4; i <= 10; i++) bass += bands[i];  // ~80-200 Hz
            bass /= 7f;
            if (bass > 0.6f) pad.pulse((bass - 0.6f) / 0.4f);
        }
    }

    @Override
    public void showPlaying(boolean playing) {
        if (listening()) player.setPlaying(playing);
    }

    private static String formatTime(double seconds) {
        long s = Math.max(0, Math.round(seconds));
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
