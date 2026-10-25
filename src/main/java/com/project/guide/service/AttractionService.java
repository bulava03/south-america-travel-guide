package com.project.guide.service;

import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.dto.WikidataAttraction;
import com.project.guide.dto.WikidataFetchResult;
import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.model.Attraction;
import com.project.guide.model.AttractionImage;
import com.project.guide.repository.AttractionRepository;
import com.project.guide.repository.AttractionImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * Existing attractions are updated by Wikidata URI and new ones are created.
     * Attractions that are no longer returned by Wikidata are removed, but only if all countries
     * were fetched successfully: otherwise attractions of a failed country would be lost.
     * Nothing changes if Wikidata requests for all countries fail.
     */
    @Transactional
    public void syncWithWikidata() {
        log.info("Starting synchronization with Wikidata...");
        
        WikidataFetchResult result = wikidataService.fetchAttractions();
        
        int created = 0;
        int updated = 0;
        Set<String> fetchedUris = new HashSet<>();
        
        for (WikidataAttraction data : result.attractions()) {
            fetchedUris.add(data.getUri());
            
            Attraction attraction = attractionRepository.findByWikidataUri(data.getUri())
                    .orElse(new Attraction());
            
            boolean isNew = attraction.getId() == null;
            
            // Update basic fields
            attraction.setWikidataUri(data.getUri());
            attraction.setName(data.getName());
            attraction.setType(data.getType());
            attraction.setLatitude(data.getLatitude());
            attraction.setLongitude(data.getLongitude());
            attraction.setWikipediaUrl(data.getWikipediaUrl());
            attraction.setPopularity(data.getPopularity());
            
            attraction.getCountries().clear();
            attraction.getCountries().addAll(data.getCountries());
            
            // Replace images, first image is the main one
            List<String> imageUrls = data.getImages();
            
            attraction.getImages().clear();
            attraction.setMainImage(imageUrls.isEmpty() ? null : imageUrls.get(0));
            for (String imageUrl : imageUrls) {
                attraction.addImage(AttractionImage.builder()
                        .imageUrl(imageUrl)
                        .build());
            }
            
            attractionRepository.save(attraction);
            
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }
        
        int removed = 0;
        if (result.isComplete()) {
            // Remove attractions that are no longer returned by Wikidata
            List<Attraction> outdated = attractionRepository.findAll().stream()
                    .filter(attraction -> !fetchedUris.contains(attraction.getWikidataUri()))
                    .collect(Collectors.toList());
            attractionRepository.deleteAll(outdated);
            removed = outdated.size();
        } else {
            log.warn("Not removing outdated attractions, failed countries: {}", result.failedCountries());
        }
        
        log.info("Synchronization complete. Created: {}, Updated: {}, Removed: {}", created, updated, removed);
    }
    
    /**
     * Gets all attractions as DTOs.
     */
    public List<AttractionDTO> getAllAttractions() {
        return attractionRepository.findAllByOrderByPopularityDesc().stream()
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
    
    private List<String> sortedCountries(Attraction attraction) {
        return attraction.getCountries().stream().sorted().collect(Collectors.toList());
    }
    
    /**
     * Converts Attraction entity to AttractionDTO.
     */
    private AttractionDTO convertToDTO(Attraction attraction) {
        return new AttractionDTO(
                attraction.getId(),
                attraction.getName(),
                attraction.getType(),
                sortedCountries(attraction),
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
                sortedCountries(attraction),
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
