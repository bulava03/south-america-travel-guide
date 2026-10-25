package com.project.guide.dto;

import java.util.Locale;

/**
 * Formatting of attraction data for pages.
 */
public final class DisplayFormat {

    private DisplayFormat() {
    }

    /**
     * Commons thumbnail of the given width: originals can be tens of megabytes.
     * A photo narrower than the width is returned as it is.
     */
    public static String thumbnail(String commonsUrl, int width) {
        return commonsUrl == null ? null : commonsUrl.replaceFirst("^http://", "https://") + "?width=" + width;
    }

    /**
     * "http://commons.wikimedia.org/wiki/Special:FilePath/X.jpg" -> "https://commons.wikimedia.org/wiki/File:X.jpg",
     * the file page with author and license.
     */
    public static String commonsPage(String commonsUrl) {
        return commonsUrl == null ? null
                : commonsUrl.replaceFirst("^http://", "https://").replace("/Special:FilePath/", "/File:");
    }
    
    /**
     * "http://www.wikidata.org/entity/Q79961" -> "https://www.wikidata.org/wiki/Q79961"
     */
    public static String wikidataPage(String entityUri) {
        return entityUri == null ? null
                : entityUri.replaceFirst("^http://", "https://").replace("/entity/", "/wiki/");
    }
    
    public static String mapUrl(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        return String.format(Locale.ROOT, "https://www.openstreetmap.org/?mlat=%.5f&mlon=%.5f#map=13/%.5f/%.5f",
                latitude, longitude, latitude, longitude);
    }
    
    /**
     * -22.951915 -> "22.9519° S"
     */
    public static String latitude(Double value) {
        return coordinate(value, "N", "S");
    }

    /**
     * -43.210464 -> "43.2105° W"
     */
    public static String longitude(Double value) {
        return coordinate(value, "E", "W");
    }

    private static String coordinate(Double value, String positive, String negative) {
        if (value == null) {
            return null;
        }
        return String.format(Locale.ROOT, "%.4f° %s", Math.abs(value), value < 0 ? negative : positive);
    }
}
