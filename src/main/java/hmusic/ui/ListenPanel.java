package hmusic.ui;

import hmusic.model.Mood;
import hmusic.stream.ListenController;
import hmusic.stream.StreamPlayer;
import hmusic.catalogue.Morceau;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le mode « Écouter » : vrais morceaux Jamendo choisis selon l'humeur.
 * Deux écrans : la connexion à Jamendo (Client ID) et le lecteur (morceau en cours + file d'attente).
 */
public class ListenPanel extends JPanel implements ListenController.View {

    private static final String DEVPORTAL = "https://devportal.jamendo.com/";

    private final ListenController controller;
    private final CardLayout cards = new CardLayout();
    private final NowPlaying nowPlaying = new NowPlaying();
    private final QueueList queueList;
    private final JLabel keyError = Theme.label(" ", Font.PLAIN, 12.5f, new Color(0xFF6B6B));
    private final HintField keyField = new HintField("Colle ton Client ID ici");

    public ListenPanel(ListenController controller) {
        this.controller = controller;
        setOpaque(false);
        setLayout(cards);
        queueList = new QueueList(controller::play);

        JPanel player = new JPanel(new BorderLayout(0, 10));
        player.setOpaque(false);
        player.add(nowPlaying, BorderLayout.CENTER);
        player.add(queueList, BorderLayout.SOUTH);

        add(buildKeySetup(), "key");
        add(player, "player");
        cards.show(this, controller.hasKey() ? "player" : "key");
        controller.addView(this);
    }

    public void setMood(Mood m) {
        nowPlaying.setMood(m);
    }

    /** Ce qu'un auditeur peut faire sur le morceau en cours (favoris, abonnements). */
    public interface ActionsAuditeur {
        boolean estFavori(Morceau m);

        void basculerFavori(Morceau m);

        boolean estArtisteFavori(String artiste);

        void basculerArtisteFavori(String artiste);

        boolean estAbonne(String artiste);

        void basculerAbonnement(String artiste);
    }

    /** @param actions null si l'utilisateur connecté n'est pas un auditeur (boutons masqués) */
    public void setActionsAuditeur(ActionsAuditeur actions) {
        nowPlaying.actionsAuditeur = actions;
        nowPlaying.repaint();
    }

    // ================================================================== écran de connexion

    private JComponent buildKeySetup() {
        JPanel card = new RoundCard();
        card.setLayout(new GridBagLayout());

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(10, 30, 10, 30));

        JLabel title = Theme.label("Connecte Jamendo", Font.BOLD, 26, Theme.TEXT);
        JLabel intro = Theme.label("<html><div style='width:360px'>Jamendo propose des milliers de morceaux complets et gratuits, "
                + "sous licence Creative Commons, classés par ambiance. Pour y accéder, il faut un identifiant "
                + "d'application <b>gratuit</b> :</div></html>", Font.PLAIN, 13.5f, Theme.MUTED);
        JLabel steps = Theme.label("<html><div style='width:360px'>"
                + "1. Crée un compte sur <b>devportal.jamendo.com</b><br>"
                + "2. Clique sur « Create a new app » (nom et site au choix)<br>"
                + "3. Copie le <b>Client ID</b> et colle-le ci-dessous</div></html>", Font.PLAIN, 13.5f, Theme.TEXT);

        keyField.setMaximumSize(new Dimension(420, 42));
        keyField.addActionListener(e -> submitKey());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(new PillButton("Valider", true, this::submitKey));
        buttons.add(Box.createHorizontalStrut(10));
        buttons.add(new PillButton("Ouvrir le site Jamendo", false, () -> Theme.openLink(DEVPORTAL)));
        buttons.setMaximumSize(new Dimension(420, 42));

        for (JComponent c : new JComponent[]{title, intro, steps, keyField, buttons, keyError}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
        }
        box.add(title);
        box.add(Box.createVerticalStrut(10));
        box.add(intro);
        box.add(Box.createVerticalStrut(14));
        box.add(steps);
        box.add(Box.createVerticalStrut(18));
        box.add(keyField);
        box.add(Box.createVerticalStrut(12));
        box.add(buttons);
        box.add(Box.createVerticalStrut(10));
        box.add(keyError);
        card.add(box);
        return card;
    }

    private void submitKey() {
        keyError.setText(" ");
        controller.setClientId(keyField.getText());
    }

    // ================================================================== ListenController.View

    @Override public void showNeedsKey(String error) {
        keyError.setText(error == null ? " " : error);
        cards.show(this, "key");
    }

    @Override public void showReady() {
        cards.show(this, "player");
    }

    @Override public void showStatus(String message, boolean error) {
        nowPlaying.setStatus(message, error);
    }

    @Override public void showNowPlaying(Morceau track) {
        cards.show(this, "player");
        nowPlaying.setTrack(track);
    }

    @Override public void showQueue(List<Morceau> upcoming) {
        queueList.setTracks(upcoming);
    }

    @Override public void showSpectrum(float[] bands) {
        nowPlaying.setSpectrum(bands);
    }

    @Override public void showPlaying(boolean playing) {
        nowPlaying.setPlaying(playing);
    }

    // ================================================================== composants

    /** Fond de carte arrondi. */
    private static class RoundCard extends JPanel {
        RoundCard() {
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hints(g);
            RoundRectangle2D rr = new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 28, 28);
            g.setColor(Theme.CARD);
            g.fill(rr);
            g.setColor(Theme.BORDER);
            g.draw(rr);
            g.dispose();
        }
    }

    /** Le morceau en cours : pochette, infos, tags, égaliseur réactif. */
    private static final class NowPlaying extends JComponent {
        private Morceau track;
        private BufferedImage image;
        private Color artColor;
        private Color moodColor = Mood.NEUTRAL.color();
        private Color glow = moodColor;
        private final float[] bars = new float[StreamPlayer.BANDS];
        private final float[] peaks = new float[StreamPlayer.BANDS];
        private float[] target = new float[StreamPlayer.BANDS];
        private boolean playing;
        private String status = " ";
        private boolean statusError;
        private ActionsAuditeur actionsAuditeur;
        /** Zones cliquables dessinées (boutons d'action). */
        private record Hit(Rectangle zone, Runnable action) {}
        private final List<Hit> hits = new ArrayList<>();
        private final Timer anim = new Timer(16, e -> animate());

        NowPlaying() {
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    for (Hit h : List.copyOf(hits)) {
                        if (h.zone().contains(e.getPoint())) {
                            h.action().run();
                            repaint();
                            return;
                        }
                    }
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    boolean onHit = hits.stream().anyMatch(h -> h.zone().contains(e.getPoint()));
                    setCursor(Cursor.getPredefinedCursor(onHit ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            });
        }

        // l'animation ne tourne que lorsque le composant est affiché dans une fenêtre
        @Override public void addNotify() {
            super.addNotify();
            anim.start();
        }

        @Override public void removeNotify() {
            anim.stop();
            super.removeNotify();
        }

        void setMood(Mood m) {
            moodColor = m.color();
        }

        void setTrack(Morceau t) {
            track = t;
            image = null;
            artColor = null;
            ImageCache.load(t.urlImage(), img -> {
                if (track != t) return;
                image = img;
                artColor = img == null ? null : averageColor(img);
                repaint();
            });
            repaint();
        }

        void setStatus(String s, boolean error) {
            status = s == null || s.isBlank() ? " " : s;
            statusError = error;
            repaint();
        }

        void setSpectrum(float[] b) {
            target = b;
        }

        void setPlaying(boolean p) {
            playing = p;
            if (!p) target = new float[StreamPlayer.BANDS];
            repaint();
        }

        private void animate() {
            Color wanted = artColor == null ? moodColor : Mood.mix(moodColor, artColor, 0.55);
            glow = Mood.mix(glow, wanted, 0.06);
            float[] t = playing ? target : new float[StreamPlayer.BANDS];
            for (int i = 0; i < bars.length; i++) {
                float v = i < t.length ? t[i] : 0;
                v = (float) Math.pow(Math.max(0, Math.min(1, (v - 0.3) / 0.6)), 1.3); // plus de contraste
                bars[i] = v > bars[i] ? bars[i] + (v - bars[i]) * 0.6f : bars[i] * 0.88f + v * 0.12f;
                peaks[i] = Math.max(bars[i], peaks[i] - 0.008f);
            }
            if (isShowing()) repaint();
        }

        private static Color averageColor(BufferedImage img) {
            long r = 0, g = 0, b = 0;
            int n = 0;
            int step = Math.max(1, Math.min(img.getWidth(), img.getHeight()) / 24);
            for (int y = 0; y < img.getHeight(); y += step) {
                for (int x = 0; x < img.getWidth(); x += step) {
                    int rgb = img.getRGB(x, y);
                    r += (rgb >> 16) & 0xff;
                    g += (rgb >> 8) & 0xff;
                    b += rgb & 0xff;
                    n++;
                }
            }
            Color c = new Color((int) (r / n), (int) (g / n), (int) (b / n));
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            return Color.getHSBColor(hsb[0], Math.min(1f, hsb[1] * 1.4f), Math.max(0.55f, hsb[2])); // plus vif
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hints(g);
            int w = getWidth(), h = getHeight();
            if (w < 50 || h < 50) {
                g.dispose();
                return;
            }
            Shape card = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 28, 28);
            g.setClip(card);
            g.setColor(Theme.CARD);
            g.fillRect(0, 0, w, h);
            float avg = 0;
            for (float b : bars) avg += b;
            avg /= bars.length;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.25, h * 0.35), (float) (Math.max(w, h) * 0.9),
                    new float[]{0f, 1f}, new Color[]{Theme.alpha(glow, 80 + 90 * avg), Theme.alpha(glow, 0)}));
            g.fillRect(0, 0, w, h);

            // pastilles du haut
            drawChip(g, "JAMENDO · CREATIVE COMMONS", 22, 22);
            g.setFont(Theme.font(Font.BOLD, 11));
            String state = track == null ? "EN ATTENTE" : playing ? "EN LECTURE" : "EN PAUSE";
            int sw = g.getFontMetrics().stringWidth(state);
            g.setColor(playing ? Mood.mix(glow, Color.WHITE, 0.5) : Theme.MUTED);
            g.drawString(state, w - 22 - sw, 39);
            g.fill(new Ellipse2D.Double(w - 38 - sw, 31, 8, 8));

            int specH = Math.max(36, Math.min(70, h / 6));
            int contentBottom = h - specH - 28; // la zone de texte s'arrête au-dessus de l'égaliseur
            if (track == null) {
                paintEmpty(g, w, contentBottom);
            } else {
                paintTrack(g, w, contentBottom);
            }
            paintSpectrum(g, 22, h - specH - 14, w - 44, specH);

            g.setClip(null);
            g.setColor(Theme.BORDER);
            g.draw(card);
            g.dispose();
        }

        private void paintEmpty(Graphics2D g, int w, int h) {
            hits.clear();
            g.setFont(Theme.font(Font.BOLD, 24));
            g.setColor(Theme.TEXT);
            String t1 = "Tes vraies musiques, selon ton humeur";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(Theme.fit(fm, t1, w - 48), Math.max(24, (w - fm.stringWidth(t1)) / 2), h / 2);
            g.setFont(Theme.font(Font.PLAIN, 13.5f));
            fm = g.getFontMetrics();
            String t2 = "Choisis une humeur, une station ou un voyage : je trouve des morceaux qui lui ressemblent.";
            g.setColor(Theme.MUTED);
            g.drawString(Theme.fit(fm, t2, w - 48), Math.max(24, (w - fm.stringWidth(t2)) / 2), h / 2 + 28);
            g.setColor(statusError ? new Color(0xFF6B6B) : Theme.MUTED);
            g.drawString(Theme.fit(fm, status, w - 48), Math.max(24, (w - fm.stringWidth(status)) / 2), h / 2 + 56);
        }

        /** @param h ordonnée maximale utilisable (au-dessus de l'égaliseur) */
        private void paintTrack(Graphics2D g, int w, int h) {
            int top = 66;
            int s = Math.max(80, Math.min(200, h - top));
            int ax = 24;
            RoundRectangle2D art = new RoundRectangle2D.Double(ax, top, s, s, 18, 18);
            // ombre portée colorée
            g.setColor(Theme.alpha(glow, 60));
            g.fill(new RoundRectangle2D.Double(ax + 4, top + 8, s, s, 18, 18));
            Graphics2D ag = (Graphics2D) g.create();
            ag.clip(art);
            if (image != null) {
                ag.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                ag.drawImage(image, ax, top, s, s, null);
            } else {
                ag.setPaint(new GradientPaint(ax, top, Mood.mix(glow, Color.WHITE, 0.2), ax + s, top + s, Mood.mix(glow, Theme.BG, 0.6)));
                ag.fill(art);
            }
            ag.dispose();

            int tx = ax + s + 24;
            int maxW = w - tx - 24;
            int y = top + 30;
            g.setFont(Theme.font(Font.BOLD, 28));
            g.setColor(Theme.TEXT);
            g.drawString(Theme.fit(g.getFontMetrics(), track.titre(), maxW), tx, y);
            y += 28;
            g.setFont(Theme.font(Font.PLAIN, 16));
            g.setColor(new Color(0xD7DAE0));
            g.drawString(Theme.fit(g.getFontMetrics(), track.artiste(), maxW), tx, y);
            if (!track.album().isBlank()) {
                y += 21;
                g.setFont(Theme.font(Font.PLAIN, 13));
                g.setColor(Theme.MUTED);
                g.drawString(Theme.fit(g.getFontMetrics(), "Album : " + track.album() + "  ·  " + track.dureeTexte(), maxW), tx, y);
            }
            // tags
            y += 16;
            int cx = tx;
            for (String tag : track.tags()) {
                g.setFont(Theme.font(Font.BOLD, 11));
                int cw = g.getFontMetrics().stringWidth(tag) + 20;
                if (cx + cw > tx + maxW) break;
                cx += drawChip(g, tag, cx, y) + 6;
            }
            y += 34;
            // boutons d'action (favoris, abonnement, lien Jamendo), avec retour à la ligne si besoin
            hits.clear();
            List<String> libelles = new ArrayList<>();
            List<Boolean> actifs = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            Morceau m = track;
            if (actionsAuditeur != null) {
                boolean fav = actionsAuditeur.estFavori(m);
                libelles.add(fav ? "Favori" : "+ Favori");
                actifs.add(fav);
                actions.add(() -> actionsAuditeur.basculerFavori(m));
                boolean artisteFav = actionsAuditeur.estArtisteFavori(m.artiste());
                libelles.add(artisteFav ? "Artiste favori" : "+ Artiste favori");
                actifs.add(artisteFav);
                actions.add(() -> actionsAuditeur.basculerArtisteFavori(m.artiste()));
                boolean abo = actionsAuditeur.estAbonne(m.artiste());
                libelles.add(abo ? "Abonné" : "S'abonner");
                actifs.add(abo);
                actions.add(() -> actionsAuditeur.basculerAbonnement(m.artiste()));
            }
            if (m.urlJamendo() != null && !m.urlJamendo().isBlank()) {
                libelles.add("Jamendo");
                actifs.add(false);
                actions.add(() -> Theme.openLink(m.urlJamendo()));
            }
            int bx = tx, rowY = y - 18;
            g.setFont(Theme.font(Font.BOLD, 12));
            FontMetrics bfm = g.getFontMetrics();
            for (int i = 0; i < libelles.size(); i++) {
                int bw = bfm.stringWidth(libelles.get(i)) + 24;
                if (bx + bw > tx + maxW && bx > tx) {   // plus de place sur la ligne : on passe à la suivante
                    bx = tx;
                    rowY += 34;
                }
                if (rowY + 28 > h + 10) break;           // plus de place au-dessus de l'égaliseur
                Rectangle r = new Rectangle(bx, rowY, bw, 28);
                drawButton(g, libelles.get(i), r, actifs.get(i));
                hits.add(new Hit(r, actions.get(i)));
                bx += bw + 8;
            }
            y = rowY + 28;
            // statut (chargement, erreurs)
            y += 22;
            if (y <= h + 8) {
                g.setFont(Theme.font(Font.PLAIN, 12.5f));
                g.setColor(statusError ? new Color(0xFF6B6B) : Theme.MUTED);
                g.drawString(Theme.fit(g.getFontMetrics(), status, maxW), tx, y);
            }
        }

        private void paintSpectrum(Graphics2D g, int x, int y, int w, int h) {
            int n = bars.length;
            double bw = w / (double) n;
            for (int i = 0; i < n; i++) {
                double v = Math.max(0.02, bars[i]);
                double bh = v * h;
                double bx = x + i * bw + 1.5;
                Color c = Mood.mix(glow, Color.WHITE, 0.15 + 0.5 * bars[i]);
                g.setColor(Theme.alpha(c, 110 + 140 * bars[i]));
                g.fill(new RoundRectangle2D.Double(bx, y + h - bh, bw - 3, bh, 4, 4));
                g.setColor(Theme.alpha(Color.WHITE, 150));
                g.fill(new RoundRectangle2D.Double(bx, y + h - peaks[i] * h - 3, bw - 3, 2, 2, 2));
            }
        }

        private void drawButton(Graphics2D g, String text, Rectangle r, boolean actif) {
            RoundRectangle2D shape = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, r.height, r.height);
            if (actif) {
                Color bg = Mood.mix(glow, Color.WHITE, 0.25);
                g.setColor(bg);
                g.fill(shape);
                g.setColor(Theme.readableOn(bg));
            } else {
                g.setColor(new Color(255, 255, 255, 18));
                g.fill(shape);
                g.setColor(new Color(255, 255, 255, 90));
                g.draw(shape);
                g.setColor(Theme.TEXT);
            }
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, r.x + 12, r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent());
        }

        private int drawChip(Graphics2D g, String text, int x, int y) {
            g.setFont(Theme.font(Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(text) + 20, h = 24;
            g.setColor(new Color(0, 0, 0, 90));
            g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
            g.setColor(Theme.TEXT);
            g.drawString(text, x + 10, y + (h - fm.getHeight()) / 2 + fm.getAscent());
            return w;
        }
    }

    /** « À suivre » : les prochains morceaux, cliquables. */
    private static final class QueueList extends JComponent {
        private static final int ROWS = 2, ROW_H = 50, HEADER = 30;
        private final java.util.function.Consumer<Morceau> onPick;
        private List<Morceau> tracks = new ArrayList<>();
        private final Map<String, BufferedImage> thumbs = new HashMap<>();
        private int hover = -1;

        QueueList(java.util.function.Consumer<Morceau> onPick) {
            this.onPick = onPick;
            setOpaque(false);
            setPreferredSize(new Dimension(100, HEADER + ROWS * ROW_H + 8));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int r = rowAt(e.getY());
                    if (r != hover) {
                        hover = r;
                        setCursor(Cursor.getPredefinedCursor(r >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                        repaint();
                    }
                }

                @Override public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override public void mouseClicked(MouseEvent e) {
                    int r = rowAt(e.getY());
                    if (r >= 0) QueueList.this.onPick.accept(tracks.get(r));
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private int rowAt(int y) {
            int r = (y - HEADER) / ROW_H;
            return y >= HEADER && r < Math.min(ROWS, tracks.size()) ? r : -1;
        }

        void setTracks(List<Morceau> list) {
            tracks = list;
            for (int i = 0; i < Math.min(ROWS, list.size()); i++) {
                Morceau t = list.get(i);
                if (!thumbs.containsKey(t.id())) {
                    ImageCache.load(t.urlImage(), img -> {
                        if (img != null) {
                            thumbs.put(t.id(), img);
                            repaint();
                        }
                    });
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hints(g);
            int w = getWidth();
            RoundRectangle2D bg = new RoundRectangle2D.Double(0, 0, w - 1, getHeight() - 1, 24, 24);
            g.setColor(Theme.CARD);
            g.fill(bg);
            g.setColor(Theme.BORDER);
            g.draw(bg);
            g.setFont(Theme.font(Font.BOLD, 11));
            g.setColor(Theme.MUTED);
            g.drawString("À SUIVRE", 18, 21);
            if (tracks.isEmpty()) {
                g.setFont(Theme.font(Font.PLAIN, 12.5f));
                g.drawString("La file d'attente se remplit selon ton humeur.", 18, HEADER + 26);
            }
            for (int i = 0; i < Math.min(ROWS, tracks.size()); i++) {
                Morceau t = tracks.get(i);
                int y = HEADER + i * ROW_H;
                if (i == hover) {
                    g.setColor(Theme.CARD_HI);
                    g.fill(new RoundRectangle2D.Double(8, y, w - 16, ROW_H - 4, 12, 12));
                }
                int s = 38, ty = y + (ROW_H - 4 - s) / 2;
                BufferedImage img = thumbs.get(t.id());
                RoundRectangle2D th = new RoundRectangle2D.Double(16, ty, s, s, 8, 8);
                if (img != null) {
                    Graphics2D tg = (Graphics2D) g.create();
                    tg.clip(th);
                    tg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    tg.drawImage(img, 16, ty, s, s, null);
                    tg.dispose();
                } else {
                    g.setColor(Theme.CARD_HI);
                    g.fill(th);
                }
                int tx = 16 + s + 12;
                g.setFont(Theme.font(Font.BOLD, 13));
                g.setColor(Theme.TEXT);
                g.drawString(Theme.fit(g.getFontMetrics(), t.titre(), w - tx - 70), tx, y + 20);
                g.setFont(Theme.font(Font.PLAIN, 12));
                g.setColor(Theme.MUTED);
                g.drawString(Theme.fit(g.getFontMetrics(), t.artiste(), w - tx - 70), tx, y + 37);
                String d = t.dureeTexte();
                g.drawString(d, w - 22 - g.getFontMetrics().stringWidth(d), y + 28);
            }
            g.dispose();
        }
    }
}
