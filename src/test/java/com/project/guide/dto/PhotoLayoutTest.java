package com.project.guide.dto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhotoLayoutTest {

    @ParameterizedTest(name = "{0}x{1} -> {2}")
    @CsvSource(nullValues = "null", value = {
            "1280, 853, COVER",     // Machu Picchu, landscape
            "1280, 914, COVER",     // Colón Theater
            "1280, 1919, FIT",      // Christ the Redeemer, vertical
            "1280, 270, FIT",       // Laguna Colorada, panorama
            "350, 247, PRINT",      // Llullaillaco, too small to fill the frame
            "null, null, COVER"     // size unknown
    })
    void testLayoutBySize(Integer width, Integer height, PhotoLayout expected) {
        assertEquals(expected, PhotoLayout.of("photo.jpg", width, height));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {"null, null", "1280, 853"})
    void testNoPhoto(Integer width, Integer height) {
        assertEquals(PhotoLayout.NONE, PhotoLayout.of(null, width, height));
    }
}
