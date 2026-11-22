package com.project.guide.dto;

import java.util.List;

/**
 * Numbers for the admin panel.
 *
 * @param countries          countries shown on the site
 * @param failedCountries    countries whose latest sync failed, sorted
 * @param syncedCountryCount countries with a saved sync result; 0 before the first sync
 */
public record AdminStatistics(
        long totalAttractions,
        long visibleAttractions,
        long hiddenAttractions,
        List<String> countries,
        int countryCount,
        List<String> failedCountries,
        int syncedCountryCount
) {
}
