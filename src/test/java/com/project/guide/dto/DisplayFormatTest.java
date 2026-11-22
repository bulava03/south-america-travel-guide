package com.project.guide.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DisplayFormatTest {

    private static final String PHOTO = "http://commons.wikimedia.org/wiki/Special:FilePath/Machu%20Picchu.jpg";

    // Paths checked against the redirects of Special:FilePath on Commons
    private static final String IGUAZU =
            "http://commons.wikimedia.org/wiki/Special:FilePath/Aerial%20Foz%20de%20Igua%C3%A7u%2026%20Nov%202005.jpg";
    private static final String UYUNI =
            "http://commons.wikimedia.org/wiki/Special:FilePath/Salar%20de%20Uyuni%20-%20panoramio%20(10).jpg";

    @Test
    void testThumbnailIsTakenFromUploadServer() {
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/Aerial_Foz_de_Igua%C3%A7u_26_Nov_2005.jpg"
                        + "/960px-Aerial_Foz_de_Igua%C3%A7u_26_Nov_2005.jpg",
                DisplayFormat.thumbnail(IGUAZU, 1500, 960));
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/9/95/Salar_de_Uyuni_-_panoramio_%2810%29.jpg"
                        + "/1920px-Salar_de_Uyuni_-_panoramio_%2810%29.jpg",
                DisplayFormat.thumbnail(UYUNI, null, 1920));
        assertNull(DisplayFormat.thumbnail(null, 1500, 960));
    }

    @Test
    void testPhotoNotWiderThanThumbnailIsTheOriginal() {
        // Commons would upscale it
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/9/91/Aerial_Foz_de_Igua%C3%A7u_26_Nov_2005.jpg",
                DisplayFormat.thumbnail(IGUAZU, 1500, 1920));
    }

    @Test
    void testOtherFormatsGoThroughFilePath() {
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Lake.tif?width=960",
                DisplayFormat.thumbnail("http://commons.wikimedia.org/wiki/Special:FilePath/Lake.tif", 4000, 960));
    }

    @Test
    void testLinksToCommonsAndWikidataPages() {
        assertEquals("https://commons.wikimedia.org/wiki/File:Machu%20Picchu.jpg", DisplayFormat.commonsPage(PHOTO));
        assertEquals("https://www.wikidata.org/wiki/Q676203",
                DisplayFormat.wikidataPage("http://www.wikidata.org/entity/Q676203"));
    }

    @Test
    void testCoordinates() {
        assertEquals("22.9519° S", DisplayFormat.latitude(-22.951915));
        assertEquals("10.9317° N", DisplayFormat.latitude(10.931666));
        assertEquals("43.2105° W", DisplayFormat.longitude(-43.210464));
        assertNull(DisplayFormat.latitude(null));
    }
}
