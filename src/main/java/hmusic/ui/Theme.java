package hmusic.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;
import java.util.Map;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Couleurs, polices et petits utilitaires graphiques partagés. */
public final class Theme {

    public static final Color BG = new Color(0x0F1014);
    public static final Color SIDEBAR = new Color(0x08090B);
    public static final Color PLAYER = new Color(0x0B0C0F);
    public static final Color CARD = new Color(0x17191F);
    public static final Color CARD_HI = new Color(0x22252E);
    public static final Color BORDER = new Color(0x2A2D36);
    public static final Color TEXT = new Color(0xF2F3F5);
    public static final Color MUTED = new Color(0x8D93A0);
    public static final Color ACCENT = new Color(0x8B6CFF);

    private static final String FAMILY = pickFamily();

    private Theme() {}

    private static String pickFamily() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[]{"Segoe UI", "Inter", "Helvetica Neue", "Arial"}) {
            if (available.contains(f)) return f;
        }
        return Font.SANS_SERIF;
    }

    public static Font font(int style, float size) {
        return new Font(FAMILY, style, 12).deriveFont(size);
    }

    public static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    public static Color alpha(Color c, double a) {
        int ai = (int) Math.max(0, Math.min(255, a));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), ai);
    }

    /** Texte clair ou foncé selon la luminosité du fond. */
    public static Color readableOn(Color bg) {
        double lum = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return lum > 150 ? new Color(0x111216) : Color.WHITE;
    }

    public static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    /** Tronque un texte avec « … » s'il dépasse la largeur donnée. */
    public static String fit(FontMetrics fm, String text, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String s = text;
        while (s.length() > 1 && fm.stringWidth(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /**
     * Installe le thème FlatLaf sombre aux couleurs de hmusic, AVANT de créer la moindre fenêtre.
     * FlatLaf modernise les composants Swing standard (listes déroulantes, boîtes de dialogue,
     * sélecteur de fichiers, menus) et remplace la barre de titre blanche de Windows par une barre sombre.
     */
    public static void install() {
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@background", "#0F1014",
                "@foreground", "#F2F3F5",
                "@accentColor", "#8B6CFF"));
        FlatDarkLaf.setup();

        // barres de titre des fenêtres
        UIManager.put("TitlePane.background", BG);
        UIManager.put("TitlePane.inactiveBackground", BG);
        UIManager.put("TitlePane.foreground", TEXT);
        UIManager.put("TitlePane.inactiveForeground", MUTED);
        // formes arrondies, bordures discrètes
        UIManager.put("Component.arc", 999);
        UIManager.put("TextComponent.arc", 999);
        UIManager.put("Button.arc", 999);
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Component.borderColor", BORDER);
        UIManager.put("Component.focusedBorderColor", MUTED);
        // listes déroulantes
        UIManager.put("ComboBox.buttonStyle", "none");
        UIManager.put("ComboBox.background", CARD_HI);
        UIManager.put("ComboBox.popupBackground", CARD_HI);
        UIManager.put("ComboBox.selectionBackground", BORDER);
        UIManager.put("ComboBox.padding", new Insets(4, 14, 4, 6));
        // menus contextuels
        UIManager.put("PopupMenu.background", CARD_HI);
        UIManager.put("PopupMenu.borderColor", BORDER);
        UIManager.put("PopupMenu.borderCornerRadius", 12);
        UIManager.put("MenuItem.selectionBackground", BORDER);
        UIManager.put("MenuItem.selectionArc", 8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 8);

        UIManager.put("ToolTip.background", CARD_HI);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("ToolTip.font", font(Font.PLAIN, 12));
        ToolTipManager.sharedInstance().setInitialDelay(350);
    }

    /** Ouvre une adresse web dans le navigateur par défaut. */
    public static void openLink(String url) {
        if (url == null || url.isBlank()) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(java.net.URI.create(url));
            }
        } catch (Exception e) {
            System.err.println("Impossible d'ouvrir " + url + " : " + e.getMessage());
        }
    }

    /** Barre de défilement fine et sombre, assortie au thème. */
    public static void darkScrollBar(JScrollBar bar) {
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(8, 8));
        bar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = BORDER;
                trackColor = SIDEBAR;
            }

            @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }

            @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }

            @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }

            @Override protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
                if (r.isEmpty()) return;
                Graphics2D g = (Graphics2D) g0.create();
                hints(g);
                g.setColor(isThumbRollover() ? MUTED : BORDER);
                g.fillRoundRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6);
                g.dispose();
            }

            private JButton zeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
    }

    /** Icône de l'application, dessinée par code : un disque dégradé traversé d'une onde. */
    public static Image appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.setPaint(new GradientPaint(0, 0, new Color(0xFF7A59), size, size, new Color(0x6A5AE0)));
        g.fillOval(0, 0, size, size);
        Path2D wave = new Path2D.Double();
        for (int x = 0; x <= size; x++) {
            double y = size / 2.0 + Math.sin(x / (double) size * Math.PI * 3) * size * 0.16;
            if (x == 0) wave.moveTo(x, y); else wave.lineTo(x, y);
        }
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(2f, size / 14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setClip(new java.awt.geom.Ellipse2D.Double(size * 0.12, 0, size * 0.76, size));
        g.draw(wave);
        g.dispose();
        return img;
    }
}
