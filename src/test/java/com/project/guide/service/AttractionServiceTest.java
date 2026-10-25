package com.project.guide.service;

import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.WikidataAttraction;
import com.project.guide.dto.WikidataFetchResult;
import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.model.Attraction;
import com.project.guide.model.AttractionImage;
import com.project.guide.repository.AttractionRepository;
import com.project.guide.repository.AttractionImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttractionServiceTest {
    
    @Mock
    private AttractionRepository attractionRepository;
    
    @Mock
    private AttractionImageRepository imageRepository;
    
    @Mock
    private WikidataService wikidataService;

    @Mock
    private WikipediaService wikipediaService;

    @InjectMocks
    private AttractionService attractionService;
    
    private Attraction testAttraction;
    
    @BeforeEach
    void setUp() {
        testAttraction = Attraction.builder()
                .id(1L)
                .wikidataUri("http://www.wikidata.org/entity/Q28572")
                .name("Machu Picchu")
                .type("World Heritage Site")
                .countries(new TreeSet<>(Set.of("Peru")))
                .popularity(140)
                .latitude(-13.1631)
                .longitude(-72.5450)
                .mainImage("http://example.com/image.jpg")
                .images(new ArrayList<>())
                .build();
    }
    
    @Test
    void testGetAllAttractions() {
        // Arrange
        List<Attraction> attractions = Arrays.asList(testAttraction);
        when(attractionRepository.findAllByOrderByPopularityDesc()).thenReturn(attractions);

        // Act
        List<AttractionDTO> result = attractionService.getAllAttractions();
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Machu Picchu", result.get(0).getName());
        assertEquals(List.of("Peru"), result.get(0).getCountries());
        
        verify(attractionRepository, times(1)).findAllByOrderByPopularityDesc();
    }
    
    @Test
    void testGetAttractionsByCountry() {
        // Arrange
        List<Attraction> attractions = Arrays.asList(testAttraction);
        when(attractionRepository.findByCountry("Peru")).thenReturn(attractions);
        
        // Act
        List<AttractionDTO> result = attractionService.getAttractionsByCountry("Peru");
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(List.of("Peru"), result.get(0).getCountries());
        
        verify(attractionRepository, times(1)).findByCountry("Peru");
    }
    
    @Test
    void testSearchAttractions() {
        // Arrange
        List<Attraction> attractions = Arrays.asList(testAttraction);
        when(attractionRepository.searchByName("Machu")).thenReturn(attractions);
        
        // Act
        List<AttractionDTO> result = attractionService.searchAttractions("Machu");
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getName().contains("Machu"));
        
        verify(attractionRepository, times(1)).searchByName("Machu");
    }
    
    @Test
    void testGetAttractionDetails() {
        // Arrange
        when(attractionRepository.findById(1L)).thenReturn(Optional.of(testAttraction));
        
        // Act
        var result = attractionService.getAttractionDetails(1L);
        
        // Assert
        assertNotNull(result);
        assertEquals("Machu Picchu", result.getName());
        assertEquals(List.of("Peru"), result.getCountries());
        assertEquals(-13.1631, result.getLatitude());
        assertEquals(-72.5450, result.getLongitude());
        
        verify(attractionRepository, times(1)).findById(1L);
    }

    @Test
    void testGetAttractionDetailsUsesWikipediaDescription() {
        // Arrange
        testAttraction.setWikipediaUrl("https://en.wikipedia.org/wiki/Machu_Picchu");
        when(attractionRepository.findById(1L)).thenReturn(Optional.of(testAttraction));
        when(wikipediaService.fetchWikipediaExtract("https://en.wikipedia.org/wiki/Machu_Picchu"))
                .thenReturn("Machu Picchu is a 15th-century Inca citadel.");

        // Act
        var result = attractionService.getAttractionDetails(1L);

        // Assert
        assertEquals("Machu Picchu is a 15th-century Inca citadel.", result.getDescription());
    }

    @Test
    void testGetAttractionDetailsNotFound() {
        // Arrange
        when(attractionRepository.findById(999L)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(AttractionNotFoundException.class, () -> {
            attractionService.getAttractionDetails(999L);
        });
        
        verify(attractionRepository, times(1)).findById(999L);
    }
    
    @Test
    void testGetAllCountries() {
        // Arrange
        List<String> countries = Arrays.asList("Peru", "Chile", "Argentina");
        when(attractionRepository.findAllDistinctCountries()).thenReturn(countries);
        
        // Act
        List<String> result = attractionService.getAllCountries();
        
        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains("Peru"));
        assertTrue(result.contains("Chile"));
        assertTrue(result.contains("Argentina"));
        
        verify(attractionRepository, times(1)).findAllDistinctCountries();
    }
    
    @Test
    void testGetStatistics() {
        // Arrange
        when(attractionRepository.count()).thenReturn(100L);
        when(imageRepository.count()).thenReturn(250L);
        when(attractionRepository.findAllDistinctCountries())
                .thenReturn(Arrays.asList("Peru", "Chile", "Argentina"));
        
        // Act
        var stats = attractionService.getStatistics();
        
        // Assert
        assertNotNull(stats);
        assertEquals(100L, stats.get("totalAttractions"));
        assertEquals(250L, stats.get("totalImages"));
        assertEquals(3, stats.get("countryCount"));
        
        verify(attractionRepository, times(1)).count();
        verify(imageRepository, times(1)).count();
        verify(attractionRepository, times(1)).findAllDistinctCountries();
    }
    
    @Test
    void testSyncCreatesNewAttraction() {
        // Arrange
        WikidataAttraction iguazu = wikidataAttraction("http://www.wikidata.org/entity/Q36332", "Iguazu Falls",
                List.of("http://example.com/a.jpg", "http://example.com/b.jpg"));
        iguazu.getCountries().addAll(List.of("Argentina", "Brazil"));
        when(wikidataService.fetchAttractions()).thenReturn(new WikidataFetchResult(List.of(iguazu), List.of()));
        when(attractionRepository.findByWikidataUri(any())).thenReturn(Optional.empty());
        when(attractionRepository.findAll()).thenReturn(List.of());

        // Act
        attractionService.syncWithWikidata();

        // Assert
        ArgumentCaptor<Attraction> saved = ArgumentCaptor.forClass(Attraction.class);
        verify(attractionRepository).save(saved.capture());
        Attraction attraction = saved.getValue();

        assertNull(attraction.getId());
        assertEquals("http://www.wikidata.org/entity/Q36332", attraction.getWikidataUri());
        assertEquals("Iguazu Falls", attraction.getName());
        assertEquals(Set.of("Argentina", "Brazil"), attraction.getCountries());
        assertEquals(99, attraction.getPopularity());
        assertEquals(-25.695, attraction.getLatitude());
        assertEquals(-54.4367, attraction.getLongitude());
        assertEquals("http://example.com/a.jpg", attraction.getMainImage());
        assertEquals(2, attraction.getImages().size());
        assertSame(attraction, attraction.getImages().get(0).getAttraction());
    }

    @Test
    void testSyncUpdatesExistingAttractionInsteadOfRecreating() {
        // Arrange
        testAttraction.addImage(AttractionImage.builder().imageUrl("http://example.com/old.jpg").build());
        WikidataAttraction updated = wikidataAttraction(testAttraction.getWikidataUri(), "Machu Picchu (updated)",
                List.of("http://example.com/new.jpg"));
        updated.getCountries().add("Peru");
        when(wikidataService.fetchAttractions()).thenReturn(new WikidataFetchResult(List.of(updated), List.of()));
        when(attractionRepository.findByWikidataUri(testAttraction.getWikidataUri()))
                .thenReturn(Optional.of(testAttraction));
        when(attractionRepository.findAll()).thenReturn(List.of(testAttraction));

        // Act
        attractionService.syncWithWikidata();

        // Assert
        verify(attractionRepository).save(testAttraction);
        assertEquals(1L, testAttraction.getId());
        assertEquals("Machu Picchu (updated)", testAttraction.getName());
        assertEquals(Set.of("Peru"), testAttraction.getCountries());
        assertEquals(1, testAttraction.getImages().size());
        assertEquals("http://example.com/new.jpg", testAttraction.getImages().get(0).getImageUrl());
        assertEquals("http://example.com/new.jpg", testAttraction.getMainImage());

        verify(attractionRepository, never()).deleteAll();
        verify(attractionRepository).deleteAll(List.of());
    }

    @Test
    void testSyncRemovesAttractionsMissingInWikidata() {
        // Arrange
        Attraction outdated = Attraction.builder()
                .id(2L)
                .wikidataUri("http://www.wikidata.org/entity/Q1")
                .name("Outdated")
                .images(new ArrayList<>())
                .build();
        WikidataAttraction current = wikidataAttraction(testAttraction.getWikidataUri(), "Machu Picchu", List.of());
        when(wikidataService.fetchAttractions()).thenReturn(new WikidataFetchResult(List.of(current), List.of()));
        when(attractionRepository.findByWikidataUri(testAttraction.getWikidataUri()))
                .thenReturn(Optional.of(testAttraction));
        when(attractionRepository.findAll()).thenReturn(List.of(testAttraction, outdated));

        // Act
        attractionService.syncWithWikidata();

        // Assert
        verify(attractionRepository).deleteAll(List.of(outdated));
    }

    @Test
    void testSyncKeepsAttractionsWhenSomeCountriesFailed() {
        // Arrange: Chile query failed, so Chilean attractions are missing in the result
        WikidataAttraction current = wikidataAttraction(testAttraction.getWikidataUri(), "Machu Picchu", List.of());
        when(wikidataService.fetchAttractions())
                .thenReturn(new WikidataFetchResult(List.of(current), List.of("Chile")));
        when(attractionRepository.findByWikidataUri(testAttraction.getWikidataUri()))
                .thenReturn(Optional.of(testAttraction));

        // Act
        attractionService.syncWithWikidata();

        // Assert
        verify(attractionRepository).save(testAttraction);
        verify(attractionRepository, never()).deleteAll(any());
    }

    @Test
    void testSyncKeepsDataWhenWikidataFails() {
        // Arrange
        when(wikidataService.fetchAttractions())
                .thenThrow(new RuntimeException("Failed to fetch attractions from Wikidata"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> attractionService.syncWithWikidata());

        verify(attractionRepository, never()).save(any());
        verify(attractionRepository, never()).deleteAll();
        verify(attractionRepository, never()).deleteAll(any());
    }

    private WikidataAttraction wikidataAttraction(String uri, String name, List<String> images) {
        WikidataAttraction attraction = new WikidataAttraction();
        attraction.setUri(uri);
        attraction.setName(name);
        attraction.setType("waterfall");
        attraction.setWikipediaUrl("https://en.wikipedia.org/wiki/Iguazu_Falls");
        attraction.setLatitude(-25.695);
        attraction.setLongitude(-54.4367);
        attraction.setPopularity(99);
        attraction.getImages().addAll(images);
        return attraction;
    }
}
