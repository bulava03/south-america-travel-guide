package com.project.guide.service;

import com.project.guide.config.CommonsProperties;
import com.project.guide.dto.WikidataAttraction;
import com.project.guide.service.CommonsClient.ImageInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhotoServiceTest {

    private static final String FILE_PATH = "http://commons.wikimedia.org/wiki/Special:FilePath/";
    private static final ImageInfo JPEG = new ImageInfo(1280, 853, "image/jpeg");

    private final CommonsClient commonsClient = mock(CommonsClient.class);
    private final PhotoService photoService = new PhotoService(commonsClient,
            new CommonsProperties("https://commons.wikimedia.org/w/api.php", 50,
                    List.of("map", "mapa", "satellite", "nasa", "flag")));

    @Test
    void testSkipsMapAndUsesNextPhotoWithItsSize() {
        WikidataAttraction tortuga = attraction(FILE_PATH + "Mapa%20isla%20la%20tortuga.svg", FILE_PATH + "Isla%20La%20Tortuga.jpg");
        when(commonsClient.imageInfo(any())).thenReturn(Map.of(
                "Mapa isla la tortuga.svg", new ImageInfo(800, 600, "image/svg+xml"),
                "Isla La Tortuga.jpg", new ImageInfo(640, 425, "image/jpeg")
        ));

        photoService.selectPhotos(List.of(tortuga));

        assertEquals(List.of(FILE_PATH + "Isla%20La%20Tortuga.jpg"), tortuga.getImages());
        assertEquals(640, tortuga.getImageWidth());
        assertEquals(425, tortuga.getImageHeight());
    }

    @Test
    void testAttractionWithoutRealPhotoGetsNoImage() {
        WikidataAttraction juncal = attraction(FILE_PATH + "JuncalNASA.png");
        when(commonsClient.imageInfo(any())).thenReturn(Map.of("JuncalNASA.png", new ImageInfo(1024, 768, "image/png")));

        photoService.selectPhotos(List.of(juncal));

        assertTrue(juncal.getImages().isEmpty());
        assertNull(juncal.getImageWidth());
    }

    @Test
    void testKeepsImagesWhenCommonsIsUnavailable() {
        WikidataAttraction machuPicchu = attraction(FILE_PATH + "Machu%20Picchu.jpg");
        when(commonsClient.imageInfo(any())).thenThrow(new IllegalStateException("Commons is down"));

        photoService.selectPhotos(List.of(machuPicchu));

        assertEquals(1, machuPicchu.getImages().size());
        assertNull(machuPicchu.getImageWidth());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mapa isla la tortuga.jpg",
            "Lago Uru Uru Bolivia Satellite map 67.09971W.png",
            "JuncalNASA.png",
            "Flag_of_Bolivia.png"
    })
    void testNotPhotos(String fileName) {
        assertFalse(photoService.isPhoto(fileName, JPEG));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Machu Picchu, Peru.jpg",
            "Mapuche ruka in Temuco.jpg",
            "Amanecer en laguna blanca largo.png"
    })
    void testPhotos(String fileName) {
        assertTrue(photoService.isPhoto(fileName, JPEG));
    }

    @Test
    void testMissingFileIsNotPhoto() {
        assertFalse(photoService.isPhoto("Deleted file.jpg", null));
    }

    @Test
    void testFileNameIsDecoded() {
        assertEquals("Iguaçu Falls, Brazil.jpg", PhotoService.fileName(FILE_PATH + "Igua%C3%A7u%20Falls%2C%20Brazil.jpg"));
    }

    private WikidataAttraction attraction(String... images) {
        WikidataAttraction attraction = new WikidataAttraction();
        attraction.setName("Test");
        attraction.getImages().addAll(List.of(images));
        return attraction;
    }
}
