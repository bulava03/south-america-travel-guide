package com.project.guide.service;

import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.model.Attraction;
import com.project.guide.model.AttractionImage;
import com.project.guide.repository.AttractionRepository;
import com.project.guide.repository.AttractionImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for managing tourist attractions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttractionService {
    
    private final AttractionRepository attractionRepository;
    private final AttractionImageRepository imageRepository;
    private final WikidataService wikidataService;
    private final WikipediaService wikipediaService;
    
    /**
     * Syncs attractions from Wikidata to local database.
     */
    @Transactional
    public void syncWithWikidata() {
        log.info("Starting synchronization with Wikidata...");

        clearAllData();
        
        Map<String, Map<String, Object>> wikidataAttractions = wikidataService.fetchAttractions();
        
        int created = 0;
        int updated = 0;
        
        for (Map.Entry<String, Map<String, Object>> entry : wikidataAttractions.entrySet()) {
            String uri = entry.getKey();
            Map<String, Object> data = entry.getValue();
            
            Attraction attraction = attractionRepository.findByWikidataUri(uri)
                    .orElse(new Attraction());
            
            boolean isNew = attraction.getId() == null;
            
            // Update basic fields
            attraction.setWikidataUri(uri);
            attraction.setName((String) data.get("name"));
            attraction.setType((String) data.get("type"));
            attraction.setCountry((String) data.get("country"));
            attraction.setWikipediaUrl((String) data.get("wikipedia"));
            
            // Parse coordinates
            if (data.containsKey("latitude") && data.containsKey("longitude")) {
                try {
                    attraction.setLatitude(Double.parseDouble((String) data.get("latitude")));
                    attraction.setLongitude(Double.parseDouble((String) data.get("longitude")));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse coordinates for {}", uri);
                }
            }
            
            // Handle images
            @SuppressWarnings("unchecked")
            List<String> imageUrls = (List<String>) data.get("images");
            
            if (imageUrls != null && !imageUrls.isEmpty()) {
                // Set first image as main
                attraction.setMainImage(imageUrls.get(0));
                
                // Clear existing images if updating
                if (!isNew) {
                    attraction.getImages().clear();
                }
                
                // Add all images
                for (String imageUrl : imageUrls) {
                    AttractionImage image = AttractionImage.builder()
                            .imageUrl(imageUrl)
                            .attraction(attraction)
                            .build();
                    attraction.addImage(image);
                }
            }
            
            attractionRepository.save(attraction);
            
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        
        log.info("Synchronization complete. Created: {}, Updated: {}", created, updated);
    }
    
    /**
     * Gets all attractions as DTOs.
     */
    public List<AttractionDTO> getAllAttractions() {
        return attractionRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets attractions filtered by country.
     */
    public List<AttractionDTO> getAttractionsByCountry(String country) {
        return attractionRepository.findByCountry(country).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Searches attractions by name.
     */
    public List<AttractionDTO> searchAttractions(String searchTerm) {
        return attractionRepository.searchByName(searchTerm).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets detailed information about a specific attraction.
     * Description is fetched from Wikipedia if the attraction has a Wikipedia URL.
     */
    public AttractionDetailDTO getAttractionDetails(Long id) {
        Attraction attraction = attractionRepository.findById(id)
                .orElseThrow(() -> new AttractionNotFoundException(id));

        AttractionDetailDTO details = convertToDetailDTO(attraction);

        String wikipediaExtract = wikipediaService.fetchWikipediaExtract(attraction.getWikipediaUrl());
        if (wikipediaExtract != null) {
            details.setDescription(wikipediaExtract);
        }

        return details;
    }
    
    /**
     * Gets all distinct countries.
     */
    public List<String> getAllCountries() {
        return attractionRepository.findAllDistinctCountries();
    }
    
    /**
     * Gets statistics.
     */
    public Map<String, Object> getStatistics() {
        long total = attractionRepository.count();
        long totalImages = imageRepository.count();
        List<String> countries = attractionRepository.findAllDistinctCountries();
        
        return Map.of(
                "totalAttractions", total,
                "totalImages", totalImages,
                "countries", countries,
                "countryCount", countries.size()
        );
    }
    
    /**
     * Clears all data from database.
     */
    @Transactional
    public void clearAllData() {
        log.warn("Clearing all data from database");
        attractionRepository.deleteAll();
    }
    
    /**
     * Converts Attraction entity to AttractionDTO.
     */
    private AttractionDTO convertToDTO(Attraction attraction) {
        return new AttractionDTO(
                attraction.getId(),
                attraction.getName(),
                attraction.getType(),
                attraction.getCountry(),
                attraction.getLatitude(),
                attraction.getLongitude(),
                attraction.getMainImage()
        );
    }
    
    /**
     * Converts Attraction entity to AttractionDetailDTO.
     */
    private AttractionDetailDTO convertToDetailDTO(Attraction attraction) {
        List<String> imageUrls = attraction.getImages().stream()
                .map(AttractionImage::getImageUrl)
                .collect(Collectors.toList());
        
        return new AttractionDetailDTO(
                attraction.getId(),
                attraction.getName(),
                attraction.getType(),
                attraction.getCountry(),
                attraction.getLatitude(),
                attraction.getLongitude(),
                attraction.getMainImage(),
                attraction.getDescription(),
                attraction.getWikipediaUrl(),
                attraction.getWikidataUri(),
                imageUrls
        );
    }
}
