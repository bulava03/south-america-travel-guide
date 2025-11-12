package com.project.guide.service;

import com.project.guide.dto.AttractionDTO;
import com.project.guide.model.Attraction;
import com.project.guide.model.AttractionImage;
import com.project.guide.repository.AttractionRepository;
import com.project.guide.repository.AttractionImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttractionServiceTest {
    
    @Mock
    private AttractionRepository attractionRepository;
    
    @Mock
    private AttractionImageRepository imageRepository;
    
    @Mock
    private WikidataService wikidataService;
    
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
                .country("Peru")
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
        when(attractionRepository.findAll()).thenReturn(attractions);
        
        // Act
        List<AttractionDTO> result = attractionService.getAllAttractions();
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Machu Picchu", result.get(0).getName());
        assertEquals("Peru", result.get(0).getCountry());
        
        verify(attractionRepository, times(1)).findAll();
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
        assertEquals("Peru", result.get(0).getCountry());
        
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
        assertEquals("Peru", result.getCountry());
        assertEquals(-13.1631, result.getLatitude());
        assertEquals(-72.5450, result.getLongitude());
        
        verify(attractionRepository, times(1)).findById(1L);
    }
    
    @Test
    void testGetAttractionDetailsNotFound() {
        // Arrange
        when(attractionRepository.findById(999L)).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
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
    void testClearAllData() {
        // Act
        attractionService.clearAllData();
        
        // Assert
        verify(attractionRepository, times(1)).deleteAll();
    }
}
