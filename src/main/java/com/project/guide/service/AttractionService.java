package com.project.guide.service;

import com.project.guide.dto.AdminAttractionDTO;
import com.project.guide.dto.AdminFilter;
import com.project.guide.dto.AdminStatistics;
import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.dto.SyncSummary;
import com.project.guide.dto.WikidataAttraction;
import com.project.guide.dto.WikidataFetchResult;
import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.model.Attraction;
import com.project.guide.model.CountrySync;
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
import java.util.function.Function;
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
    private final CountrySyncRepository countrySyncRepository;

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

        // One query with countries instead of a lookup per attraction and a lazy load per collection
        Map<String, Attraction> existing = attractionRepository.findAllByOrderByPopularityDesc().stream()
                .collect(Collectors.toMap(Attraction::getWikidataUri, Function.identity()));

        List<Attraction> created = new ArrayList<>();
        for (WikidataAttraction data : result.attractions()) {
            Attraction attraction = existing.get(data.getUri());
            if (attraction == null) {
                attraction = new Attraction();
                created.add(attraction);
            }
            attraction.updateFrom(data);
        }
        // Existing attractions are managed and saved on commit
        attractionRepository.saveAll(created);

        Set<String> fetchedUris = result.attractions().stream()
                .map(WikidataAttraction::getUri)
                .collect(Collectors.toSet());
        Set<String> fetchedCountries = result.fetchedCountries().keySet();
        List<Attraction> outdated = existing.values().stream()
                .filter(attraction -> !attraction.isHidden())
                .filter(attraction -> !fetchedUris.contains(attraction.getWikidataUri()))
                .filter(attraction -> fetchedCountries.containsAll(attraction.getCountries()))
                .toList();
        attractionRepository.deleteAll(outdated);

        log.info("Synchronization complete. Created: {}, Updated: {}, Removed: {}, Failed countries: {}",
                created.size(), result.attractions().size() - created.size(), outdated.size(),
                result.failedCountries().keySet());
        return new SyncSummary(result.attractions().size(), created.size(), outdated.size());
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
     * Attraction details with the description saved by the sync; hidden attractions are not found.
     */
    public AttractionDetailDTO getAttractionDetails(Long id) {
        return attractionRepository.findById(id)
                .filter(found -> !found.isHidden())
                .map(AttractionDetailDTO::from)
                .orElseThrow(() -> new AttractionNotFoundException(id));
    }

    /**
     * The most popular other attractions of the attraction's first country.
     */
    @Transactional(readOnly = true)
    public List<AttractionDTO> findMoreInCountry(AttractionDTO attraction, int limit) {
        if (attraction.getCountries().isEmpty()) {
            return List.of();
        }
        return attractionRepository
                .findOthersInCountry(attraction.getCountries().get(0), attraction.getId(), Limit.of(limit)).stream()
                .map(AttractionDTO::from)
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
     * Numbers for the admin panel.
     */
    public AdminStatistics getStatistics() {
        List<String> countries = attractionRepository.findAllDistinctCountries();
        long total = attractionRepository.count();
        long hidden = attractionRepository.countByHiddenTrue();
        List<CountrySync> syncs = countrySyncRepository.findAll();
        List<String> failedCountries = syncs.stream()
                .filter(sync -> !sync.isSucceeded())
                .map(CountrySync::getCountry)
                .sorted()
                .toList();
        return new AdminStatistics(total, total - hidden, hidden,
                countries, countries.size(), failedCountries, syncs.size());
    }
}
