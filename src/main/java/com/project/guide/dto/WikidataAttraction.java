package com.project.guide.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Attraction data fetched from Wikidata.
 */
@Data
public class WikidataAttraction {
    private String uri;
    private String name;
    private String type;
    private Set<String> countries = new LinkedHashSet<>();
    private Double latitude;
    private Double longitude;
    private List<String> images = new ArrayList<>();
    /** Size of the first image, set from Wikimedia Commons; null if unknown. */
    private Integer imageWidth;
    private Integer imageHeight;
    private String wikipediaUrl;
    private int popularity;

    /**
     * Combines the same attraction returned for two countries (e.g. Iguazu Falls).
     */
    public WikidataAttraction mergeWith(WikidataAttraction other) {
        countries.addAll(other.countries);
        other.images.stream().filter(image -> !images.contains(image)).forEach(images::add);
        return this;
    }
}
