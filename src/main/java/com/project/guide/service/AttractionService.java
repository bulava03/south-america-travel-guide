package com.project.guide.service;

import com.project.guide.dto.AdminAttractionDTO;
import com.project.guide.dto.AdminFilter;
import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.dto.SyncSummary;
import com.project.guide.dto.WikidataAttraction;
import com.project.guide.dto.WikidataFetchResult;
import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.model.Attraction;
import com.project.guide.model.CountrySync;
import com.project.guide.repository.AttractionImageRepository;
import com.project.guide.repository.AttractionRepository;
import com.project.guide.repository.CountrySyncRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing tourist attractions.
 * Fetching from Wikidata runs in SyncService; this service saves the result.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttractionService {

    private final AttractionRepository attractionRepository;
    private final AttractionImageRepository imageRepository;
    private final CountrySyncRepository countrySyncRepository;
    private final WikipediaService wikipediaService;

    /**
     * Saves attractions fetched from Wikidata and the result of every country.
     * Existing attractions are updated by Wikidata URI and new ones are created.
     * An attraction that is no longer returned is removed only if all its countries were fetched:
     * e.g. Iguazu Falls (Argentina, Brazil) stays when only Brazil is synced or Argentina failed.
     * Hidden attractions are never fetched, so they are neither updated nor removed.
     * If all countries failed, only their results are saved.
     */
    @Transactional
    public SyncSummary saveSyncResult(WikidataFetchResult result) {
        saveCountryResults(result);
        if (result.isEmpty()) {
            log.warn("Nothing fetched, failed countries: {}", result.failedCountries().keySet());
            return new SyncSummary(0, 0, 0);
        }

        int created = 0;
        for (WikidataAttraction data : result.attractions()) {
            Attraction attraction = attractionRepository.findByWikidataUri(data.getUri()).orElseGet(Attraction::new);
            if (attraction.getId() == null) {
                created++;
            }
            attraction.updateFrom(data);
            attractionRepository.save(attraction);
        }

        Set<String> fetchedUris = result.attractions().stream()
                .map(WikidataAttraction::getUri)
                .collect(Collectors.toSet());
        Set<String> fetchedCountries = result.fetchedCountries().keySet();
        List<Attraction> outdated = attractionRepository.findAll().stream()
                .filter(attraction -> !attraction.isHidden())
                .filter(attraction -> !fetchedUris.contains(attraction.getWikidataUri()))
                .filter(attraction -> fetchedCountries.containsAll(attraction.getCountries()))
                .toList();
        attractionRepository.deleteAll(outdated);

        log.info("Synchronization complete. Created: {}, Updated: {}, Removed: {}, Failed countries: {}",
                created, result.attractions().size() - created, outdated.size(), result.failedCountries().keySet());
        return new SyncSummary(result.attractions().size(), created, outdated.size());
    }

    private void saveCountryResults(WikidataFetchResult result) {
        LocalDateTime now = LocalDateTime.now();
        List<CountrySync> results = new ArrayList<>();
        result.fetchedCountries().forEach((country, count) ->
                results.add(new CountrySync(country, now, true, count, null)));
        result.failedCountries().forEach((country, message) ->
                results.add(new CountrySync(country, now, false, 0, message)));
        countrySyncRepository.saveAll(results);
    }

    public Set<String> findHiddenWikidataUris() {
        return new HashSet<>(attractionRepository.findHiddenWikidataUris());
    }

    @Transactional
    public void setHidden(Long id, boolean hidden) {
        Attraction attraction = attractionRepository.findById(id)
                .orElseThrow(() -> new AttractionNotFoundException(id));
        attraction.setHidden(hidden);
        log.info("{} is {}", attraction.getName(), hidden ? "hidden" : "visible again");
    }

    /**
     * All attractions including hidden ones, most popular first.
     * Search by name and the country filter are combined.
     */
    @Transactional(readOnly = true)
    public List<AdminAttractionDTO> findForAdmin(String search, String country, AdminFilter filter) {
        return attractionRepository.findAllByOrderByPopularityDesc().stream()
                .filter(filter::matches)
                .filter(attraction -> !StringUtils.hasText(country) || attraction.getCountries().contains(country))
                .filter(attraction -> !StringUtils.hasText(search)
                        || attraction.getName().toLowerCase().contains(search.trim().toLowerCase()))
                .map(AdminAttractionDTO::from)
                .toList();
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
            attractions = attractionRepository.findByHiddenFalseOrderByPopularityDesc();
        }
        return attractions.stream().map(AttractionDTO::from).toList();
    }

    /**
     * Attraction details with the description fetched from Wikipedia; hidden attractions are not found.
     */
    public AttractionDetailDTO getAttractionDetails(Long id) {
        Attraction attraction = attractionRepository.findById(id)
                .filter(found -> !found.isHidden())
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
        return attractionRepository.findByHiddenFalseOrderByPopularityDesc(Limit.of(limit)).stream()
                .map(AttractionDTO::from)
                .toList();
    }

    public List<String> getAllCountries() {
        return attractionRepository.findAllDistinctCountries();
    }

    /**
     * Numbers for the admin panel; "countries" are the countries shown on the site,
     * "syncedCountryCount" the countries with a saved sync result (none before the first sync).
     */
    public Map<String, Object> getStatistics() {
        List<String> countries = attractionRepository.findAllDistinctCountries();
        long total = attractionRepository.count();
        long hidden = attractionRepository.countByHiddenTrue();
        List<CountrySync> syncs = countrySyncRepository.findAll();
        List<String> failedCountries = syncs.stream()
                .filter(sync -> !sync.isSucceeded())
                .map(CountrySync::getCountry)
                .sorted()
                .toList();
        return Map.of(
                "totalAttractions", total,
                "visibleAttractions", total - hidden,
                "hiddenAttractions", hidden,
                "withoutPhoto", attractionRepository.countByMainImageIsNull(),
                "totalImages", imageRepository.count(),
                "countries", countries,
                "countryCount", countries.size(),
                "failedCountries", failedCountries,
                "syncedCountryCount", syncs.size()
        );
    }
}
