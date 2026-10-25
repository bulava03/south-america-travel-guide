package com.project.guide.dto;

/**
 * How the main photo fills a card's 4:3 frame.
 */
public enum PhotoLayout {
    /** Landscape photo: cropped from the centre to fill the frame. */
    COVER,
    /** Vertical or panoramic photo: shown whole over its own blurred copy. */
    FIT,
    /** Small photo: shown as a print at a size where it still looks sharp. */
    PRINT,
    /** No photo: the name is shown instead. */
    NONE;

    private static final int MIN_SHARP_WIDTH = 800;
    private static final double MIN_COVER_RATIO = 1.15;
    private static final double MAX_COVER_RATIO = 1.9;

    /**
     * @param width  photo width, null if unknown
     * @param height photo height, null if unknown
     */
    public static PhotoLayout of(String photo, Integer width, Integer height) {
        if (photo == null) {
            return NONE;
        }
        if (width == null || height == null || height == 0) {
            return COVER;
        }
        if (width < MIN_SHARP_WIDTH) {
            return PRINT;
        }
        double ratio = (double) width / height;
        return ratio >= MIN_COVER_RATIO && ratio <= MAX_COVER_RATIO ? COVER : FIT;
    }
}
