package com.project.guide.dto;

import org.springframework.util.DigestUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Formatting of attraction data for pages.
 */
public final class DisplayFormat {

    private static final String FILE_PATH = "/wiki/Special:FilePath/";
    private static final String UPLOAD = "https://upload.wikimedia.org/wikipedia/commons/";
    /** Formats whose thumbnails are named "{width}px-{file name}"; e.g. TIFF ones are not. */
    private static final Set<String> THUMBNAIL_FORMATS = Set.of("jpg", "jpeg", "png");

    private DisplayFormat() {
    }

    /**
     * Commons photo of the given width straight from upload.wikimedia.org: Special:FilePath would cost
     * two redirects. The width must be a standard one (250, 960, 1920...), Commons rejects others.
     * Originals can be tens of megabytes, so a thumbnail is used, but a photo not wider than the width
     * is returned as the original: Commons would upscale it. Other formats still go through Special:FilePath.
     *
     * @param originalWidth width of the original, null if unknown
     */
    public static String thumbnail(String commonsUrl, Integer originalWidth, int width) {
        if (commonsUrl == null) {
            return null;
        }
        String url = commonsUrl.replaceFirst("^http://", "https://");
        int start = url.indexOf(FILE_PATH);
        if (start < 0) {
            return url + "?width=" + width;
        }
        // "Machu%20Picchu.jpg" -> "Machu_Picchu.jpg", the name the file is stored under
        String fileName = URLDecoder.decode(url.substring(start + FILE_PATH.length()), UTF_8).replace(' ', '_');
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!THUMBNAIL_FORMATS.contains(extension)) {
            return url + "?width=" + width;
        }

        // The directory is the first one and two hex digits of the MD5 of the name
        String md5 = DigestUtils.md5DigestAsHex(fileName.getBytes(UTF_8));
        String encoded = URLEncoder.encode(fileName, UTF_8);
        String original = md5.charAt(0) + "/" + md5.substring(0, 2) + "/" + encoded;
        if (originalWidth != null && originalWidth <= width) {
            return UPLOAD + original;
        }
        return UPLOAD + "thumb/" + original + "/" + width + "px-" + encoded;
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
