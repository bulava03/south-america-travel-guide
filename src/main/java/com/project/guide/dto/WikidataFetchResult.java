package com.project.guide.dto;

import java.util.List;
import java.util.Map;

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
     * All requested countries failed.
     */
    public boolean isEmpty() {
        return fetchedCountries.isEmpty();
    }
}
