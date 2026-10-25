package com.project.guide.model;

import com.project.guide.dto.WikidataAttraction;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
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
    private String mainImage;

    @Column
    private Integer mainImageWidth;

    @Column
    private Integer mainImageHeight;

    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(length = 500)
    private String wikipediaUrl;
    
    @OneToMany(mappedBy = "attraction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AttractionImage> images = new ArrayList<>();
    
    /**
     * Helper method to add an image to this attraction.
     */
    public void addImage(AttractionImage image) {
        images.add(image);
        image.setAttraction(this);
    }

    /**
     * Replaces all fields fetched from Wikidata; the first image becomes the main one.
     */
    public void updateFrom(WikidataAttraction data) {
        wikidataUri = data.getUri();
        name = data.getName();
        type = data.getType();
        latitude = data.getLatitude();
        longitude = data.getLongitude();
        wikipediaUrl = data.getWikipediaUrl();
        popularity = data.getPopularity();

        countries.clear();
        countries.addAll(data.getCountries());

        images.clear();
        mainImage = data.getImages().isEmpty() ? null : data.getImages().get(0);
        mainImageWidth = data.getImageWidth();
        mainImageHeight = data.getImageHeight();
        data.getImages().forEach(url -> addImage(AttractionImage.builder().imageUrl(url).build()));
    }
}
