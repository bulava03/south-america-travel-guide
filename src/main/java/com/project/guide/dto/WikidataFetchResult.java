package com.project.guide.dto;

import java.util.List;

/**
 * Result of fetching attractions from Wikidata.
 *
 * @param attractions     attractions from all countries, without duplicates
 * @param failedCountries countries whose queries failed; their attractions are missing in the result
 */
public record WikidataFetchResult(List<WikidataAttraction> attractions, List<String> failedCountries) {

    public boolean isComplete() {
        return failedCountries.isEmpty();
    }
}
