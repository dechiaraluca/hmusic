package hmusic.ui;

import hmusic.model.Journey;
import hmusic.model.Station;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Barre latérale : logo, stations d'humeur, voyages guidés, journal. */
public class Sidebar extends JPanel {

    private final List<SidebarItem> items = new ArrayList<>();

    public Sidebar(Consumer<Station> onStation, Consumer<Journey> onJourney, Runnable onJournal) {
        super(new BorderLayout());
        setBackground(Theme.SIDEBAR);
        setPreferredSize(new Dimension(272, 0));

        ScrollablePanel content = new ScrollablePanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(22, 14, 16, 14));

        content.add(logo());
        content.add(Box.createVerticalStrut(22));

        content.add(section("VOYAGES GUIDÉS"));
        for (Journey j : Journey.PRESETS) {
            SidebarItem it = new SidebarItem(j.name(), j.description(), j.from().color(), j.to().color());
            it.setAction(() -> {
                select(it);
                onJourney.accept(j);
            });
            items.add(it);
            content.add(it);
        }
        content.add(Box.createVerticalStrut(18));

        content.add(section("STATIONS D'HUMEUR"));
        for (Station s : Station.PRESETS) {
            SidebarItem it = new SidebarItem(s.name(), s.tagline(), s.mood().color(), s.mood().color());
            it.setAction(() -> {
                select(it);
                onStation.accept(s);
            });
            items.add(it);
            content.add(it);
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        Theme.darkScrollBar(scroll.getVerticalScrollBar());
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(8, 14, 14, 14));
        SidebarItem journal = new SidebarItem("Mon journal d'humeur", "Ton humeur au fil des jours",
                new Color(0x2EC4B6), new Color(0x6A5AE0));
        journal.setAction(onJournal);
        bottom.add(journal, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    public void clearSelection() {
        for (SidebarItem it : items) it.setSelected(false);
    }

    private void select(SidebarItem item) {
        for (SidebarItem it : items) it.setSelected(it == item);
    }

    private JComponent section(String text) {
        JLabel l = Theme.label(text, Font.BOLD, 11, Theme.MUTED);
        l.setBorder(new EmptyBorder(0, 8, 8, 0));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JComponent logo() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setBorder(new EmptyBorder(0, 8, 0, 0));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JLabel icon = new JLabel(new ImageIcon(Theme.appIcon(40)));
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.add(Theme.label("hmusic", Font.BOLD, 22, Theme.TEXT));
        texts.add(Theme.label("musique générative à l'humeur", Font.PLAIN, 11, Theme.MUTED));
        p.add(icon, BorderLayout.WEST);
        p.add(texts, BorderLayout.CENTER);
        return p;
    }

    /** Panneau qui suit la largeur de la zone de défilement. */
    private static final class ScrollablePanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
