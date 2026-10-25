package com.project.guide.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DisplayFormatTest {

    private static final String PHOTO = "http://commons.wikimedia.org/wiki/Special:FilePath/Machu%20Picchu.jpg";

    @Test
    void testThumbnailIsHttpsWithWidth() {
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Machu%20Picchu.jpg?width=800",
                DisplayFormat.thumbnail(PHOTO, 800));
        assertNull(DisplayFormat.thumbnail(null, 800));
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
