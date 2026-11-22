package com.project.guide.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Result of fetching attractions from Wikidata.
 *
 * @param attractions        attractions of the fetched countries, without duplicates
 * @param fetchedCountries   country -> number of selected attractions
 * @param failedCountries    country -> error; their attractions are missing in the result
 */
public record WikidataFetchResult(
        List<WikidataAttraction> attractions,
        Map<String, Integer> fetchedCountries,
        Map<String, String> failedCountries
) {

    public boolean isComplete() {
        return failedCountries.isEmpty();
    }

    /**
     * The same result with only the given attractions; place counts of the countries are reduced
     * by the attractions left out.
     */
    public WikidataFetchResult withAttractions(List<WikidataAttraction> kept) {
        // By URI: WikidataAttraction is mutable, so its hashCode may change
        Set<String> keptUris = kept.stream().map(WikidataAttraction::getUri).collect(Collectors.toSet());
        Map<String, Integer> counts = new HashMap<>(fetchedCountries);
        attractions.stream()
                .filter(attraction -> !keptUris.contains(attraction.getUri()))
                .flatMap(attraction -> attraction.getCountries().stream())
                .forEach(country -> counts.computeIfPresent(country, (key, count) -> count - 1));
        return new WikidataFetchResult(kept, counts, failedCountries);
    }

    /**
     * All requested countries failed.
     */
    public boolean isEmpty() {
        return fetchedCountries.isEmpty();
    }
}
