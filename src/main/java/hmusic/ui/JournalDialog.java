package hmusic.ui;

import hmusic.data.MoodJournal;
import hmusic.model.Mood;
import hmusic.model.Sector;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Fenêtre du journal : un « ruban » de couleurs de tes humeurs + la liste détaillée. */
public class JournalDialog extends JDialog {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.FRANCE);

    public JournalDialog(Frame owner, MoodJournal journal, Runnable onCleared) {
        super(owner, "Mon journal d'humeur", true);
        List<MoodJournal.Entry> entries = new ArrayList<>(journal.entries());

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(22, 24, 20, 24));

        // en-tête
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(Theme.label("Mon journal d'humeur", Font.BOLD, 22, Theme.TEXT));
        header.add(Box.createVerticalStrut(4));
        header.add(Theme.label(summary(journal, entries), Font.PLAIN, 13, Theme.MUTED));
        header.add(Box.createVerticalStrut(14));
        MoodRibbon ribbon = new MoodRibbon(entries);
        ribbon.setAlignmentX(LEFT_ALIGNMENT);
        header.add(ribbon);
        root.add(header, BorderLayout.NORTH);

        // liste (plus récent en premier)
        List<MoodJournal.Entry> reversed = new ArrayList<>(entries);
        Collections.reverse(reversed);
        JList<MoodJournal.Entry> list = new JList<>(reversed.toArray(new MoodJournal.Entry[0]));
        list.setBackground(Theme.BG);
        list.setCellRenderer(new EntryRenderer());
        list.setFixedCellHeight(46);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.BG);
        Theme.darkScrollBar(scroll.getVerticalScrollBar());
        root.add(scroll, BorderLayout.CENTER);

        // boutons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(new PillButton("Effacer le journal", false, () -> {
            int ok = JOptionPane.showConfirmDialog(this, "Effacer définitivement tout le journal ?",
                    "Journal", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                journal.clear();
                onCleared.run();
                dispose();
            }
        }));
        buttons.add(new PillButton("Fermer", true, this::dispose));
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(560, 600);
        setLocationRelativeTo(owner);
    }

    private static String summary(MoodJournal journal, List<MoodJournal.Entry> entries) {
        if (entries.isEmpty()) return "Aucune entrée pour l'instant : choisis une humeur pour commencer.";
        String s = entries.size() + " moment" + (entries.size() > 1 ? "s" : "") + " enregistré" + (entries.size() > 1 ? "s" : "");
        Sector dominant = journal.dominantSince(LocalDateTime.now().minusDays(7)).orElse(null);
        if (dominant != null) s += " · cette semaine, tu étais surtout : " + dominant.label().toLowerCase(Locale.FRANCE);
        return s;
    }

    /** Une bande où chaque humeur enregistrée devient une tranche de couleur. */
    private static final class MoodRibbon extends JComponent {
        private final List<MoodJournal.Entry> entries;

        MoodRibbon(List<MoodJournal.Entry> entries) {
            this.entries = entries;
            setPreferredSize(new Dimension(500, 34));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            setToolTipText("Tes humeurs, de la plus ancienne (gauche) à la plus récente (droite)");
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hints(g);
            int w = getWidth(), h = getHeight();
            RoundRectangle2D rr = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, h, h);
            g.setColor(Theme.CARD);
            g.fill(rr);
            g.clip(rr);
            int n = Math.min(entries.size(), 120);
            int start = entries.size() - n;
            for (int i = 0; i < n; i++) {
                Mood m = entries.get(start + i).mood();
                int x0 = (int) Math.round(i * (double) w / n), x1 = (int) Math.round((i + 1) * (double) w / n);
                Color next = i + 1 < n ? entries.get(start + i + 1).mood().color() : m.color();
                g.setPaint(new GradientPaint(x0, 0, m.color(), x1, 0, next));
                g.fillRect(x0, 0, x1 - x0 + 1, h);
            }
            g.dispose();
        }
    }

    private static final class EntryRenderer extends JComponent implements ListCellRenderer<MoodJournal.Entry> {
        private MoodJournal.Entry entry;
        private boolean selected;

        @Override
        public Component getListCellRendererComponent(JList<? extends MoodJournal.Entry> list, MoodJournal.Entry value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            entry = value;
            selected = isSelected;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme.hints(g);
            int w = getWidth(), h = getHeight();
            g.setColor(selected ? Theme.CARD_HI : Theme.BG);
            g.fillRect(0, 0, w, h);
            Mood m = entry.mood();
            g.setColor(m.color());
            g.fill(new Ellipse2D.Double(14, h / 2.0 - 7, 14, 14));
            g.setFont(Theme.font(Font.BOLD, 13));
            g.setColor(Theme.TEXT);
            g.drawString(m.sector().label(), 40, h / 2 - 3);
            g.setFont(Theme.font(Font.PLAIN, 11.5f));
            g.setColor(Theme.MUTED);
            g.drawString(Theme.fit(g.getFontMetrics(), entry.source(), w - 200), 40, h / 2 + 13);
            String date = FMT.format(entry.time());
            g.drawString(date, w - 14 - g.getFontMetrics().stringWidth(date), h / 2 + 4);
            g.dispose();
        }
    }
}
