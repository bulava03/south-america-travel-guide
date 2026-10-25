package com.project.guide.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Wikidata sources, attraction catalog and sync limits from wikidata.yml.
 *
 * @param countries     Wikidata id -> country name
 * @param types         attraction types in order of priority
 * @param excludedTypes types that are never shown
 */
@ConfigurationProperties(prefix = "wikidata")
public record WikidataProperties(
        String sparqlEndpoint,
        String searchEndpoint,
        Duration timeout,
        int attempts,
        int searchLimit,
        int perCountryLimit,
        int maxPerType,
        Map<String, String> countries,
        List<AttractionType> types,
        List<String> excludedTypes
) {

    public record AttractionType(String id, String label) {
    }
}
