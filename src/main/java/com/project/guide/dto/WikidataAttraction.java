package com.project.guide.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Attraction data fetched from Wikidata.
 * One attraction may come in several result rows (per country, type and image), they are merged here.
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
    private String wikipediaUrl;
    private int popularity;
}
