package com.project.guide.service;

import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.dto.WikidataAttraction;
import com.project.guide.dto.WikidataFetchResult;
import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.model.Attraction;
import com.project.guide.repository.AttractionImageRepository;
import com.project.guide.repository.AttractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final PhotoService photoService;

    /**
     * Syncs attractions from Wikidata to local database.
     * Existing attractions are updated by Wikidata URI and new ones are created.
     * Attractions that are no longer returned by Wikidata are removed, but only if all countries
     * were fetched successfully: otherwise attractions of a failed country would be lost.
     * Nothing changes if Wikidata requests for all countries fail.
     * Images that are not photos (maps, flags, drawings) are dropped, see PhotoService.
     */
    @Transactional
    public void syncWithWikidata() {
        WikidataFetchResult result = wikidataService.fetchAttractions();
        photoService.selectPhotos(result.attractions());

        int created = 0;
        for (WikidataAttraction data : result.attractions()) {
            Attraction attraction = attractionRepository.findByWikidataUri(data.getUri()).orElseGet(Attraction::new);
            if (attraction.getId() == null) {
                created++;
            }
            attraction.updateFrom(data);
            attractionRepository.save(attraction);
        }

        int removed = 0;
        if (result.isComplete()) {
            Set<String> fetchedUris = result.attractions().stream()
                    .map(WikidataAttraction::getUri)
                    .collect(Collectors.toSet());
            List<Attraction> outdated = attractionRepository.findAll().stream()
                    .filter(attraction -> !fetchedUris.contains(attraction.getWikidataUri()))
                    .toList();
            attractionRepository.deleteAll(outdated);
            removed = outdated.size();
        } else {
            log.warn("Not removing outdated attractions, failed countries: {}", result.failedCountries());
        }

        log.info("Synchronization complete. Created: {}, Updated: {}, Removed: {}",
                created, result.attractions().size() - created, removed);
    }

    /**
     * Attractions, most popular first. Search by name takes precedence over the country filter;
     * without both all attractions are returned.
     */
    public List<AttractionDTO> findAttractions(String country, String search) {
        List<Attraction> attractions;
        if (StringUtils.hasText(search)) {
            attractions = attractionRepository.searchByName(search);
        } else if (StringUtils.hasText(country)) {
            attractions = attractionRepository.findByCountry(country);
        } else {
            attractions = attractionRepository.findAllByOrderByPopularityDesc();
        }
        return attractions.stream().map(AttractionDTO::from).toList();
    }

    /**
     * Attraction details with the description fetched from Wikipedia.
     */
    public AttractionDetailDTO getAttractionDetails(Long id) {
        Attraction attraction = attractionRepository.findById(id)
                .orElseThrow(() -> new AttractionNotFoundException(id));

        AttractionDetailDTO details = AttractionDetailDTO.from(attraction);
        String wikipediaExtract = wikipediaService.fetchWikipediaExtract(attraction.getWikipediaUrl());
        if (wikipediaExtract != null) {
            details.setDescription(wikipediaExtract);
        }
        return details;
    }

    /**
     * The most popular other attractions of the attraction's first country.
     */
    public List<AttractionDTO> findMoreInCountry(AttractionDetailDTO attraction, int limit) {
        if (attraction.getCountries().isEmpty()) {
            return List.of();
        }
        return findAttractions(attraction.getCountries().get(0), null).stream()
                .filter(other -> !other.getId().equals(attraction.getId()))
                .limit(limit)
                .toList();
    }

    /**
     * The most popular attractions of all countries.
     */
    @Transactional(readOnly = true)
    public List<AttractionDTO> findMostVisited(int limit) {
        return attractionRepository.findByOrderByPopularityDesc(Limit.of(limit)).stream()
                .map(AttractionDTO::from)
                .toList();
    }

    public List<String> getAllCountries() {
        return attractionRepository.findAllDistinctCountries();
    }

    public Map<String, Object> getStatistics() {
        List<String> countries = attractionRepository.findAllDistinctCountries();
        return Map.of(
                "totalAttractions", attractionRepository.count(),
                "totalImages", imageRepository.count(),
                "countries", countries,
                "countryCount", countries.size()
        );
    }
}
