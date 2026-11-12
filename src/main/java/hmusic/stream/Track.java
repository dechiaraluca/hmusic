package hmusic.stream;

import java.util.List;

/** Un vrai morceau, tel que renvoyé par l'API Jamendo. */
public record Track(String id, String title, String artist, String album, int durationSeconds,
                    String audioUrl, String imageUrl, String shareUrl, String licenseUrl, List<String> tags) {

    public String durationText() {
        return String.format("%d:%02d", durationSeconds / 60, durationSeconds % 60);
    }
}
