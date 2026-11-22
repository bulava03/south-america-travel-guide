package com.project.guide.model;

import com.project.guide.dto.WikidataAttraction;
import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Entity representing a tourist attraction in South America.
 */
@Entity
@Table(name = "attractions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attraction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 500)
    private String wikidataUri;
    
    @Column(nullable = false, length = 500)
    private String name;
    
    @Column(length = 200)
    private String type;
    
    /**
     * Countries the attraction is located in, usually one.
     */
    @ElementCollection
    @CollectionTable(name = "attraction_countries", joinColumns = @JoinColumn(name = "attraction_id"))
    @Column(name = "country", nullable = false, length = 100)
    @Builder.Default
    private Set<String> countries = new TreeSet<>();

    /**
     * Number of Wikipedia language editions, used to rank attractions.
     */
    @Column(nullable = false)
    private int popularity;

    @Column
    private Double latitude;
    
    @Column
    private Double longitude;
    
    @Column(length = 1000)
    private String image;

    @Column
    private Integer imageWidth;

    @Column
    private Integer imageHeight;

    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(length = 500)
    private String wikipediaUrl;

    /**
     * Hidden by the admin: not shown on the site and skipped by the sync.
     */
    @Column(nullable = false)
    private boolean hidden;
    
    /**
     * Replaces all fields fetched from Wikidata and Wikipedia; the first photo is shown.
     * If the description of the same article was not fetched this time, the previous one is kept.
     */
    public void updateFrom(WikidataAttraction data) {
        if (data.getDescription() != null || !Objects.equals(wikipediaUrl, data.getWikipediaUrl())) {
            description = data.getDescription();
        }
        wikidataUri = data.getUri();
        name = data.getName();
        type = data.getType();
        latitude = data.getLatitude();
        longitude = data.getLongitude();
        wikipediaUrl = data.getWikipediaUrl();
        popularity = data.getPopularity();

        countries.clear();
        countries.addAll(data.getCountries());

        image = data.getImages().isEmpty() ? null : data.getImages().get(0);
        imageWidth = data.getImageWidth();
        imageHeight = data.getImageHeight();
    }
}
