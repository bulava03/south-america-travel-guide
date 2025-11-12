package com.project.guide.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

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
    
    @Column(length = 100)
    private String country;
    
    @Column
    private Double latitude;
    
    @Column
    private Double longitude;
    
    @Column(length = 1000)
    private String mainImage;
    
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
     * Helper method to remove an image from this attraction.
     */
    public void removeImage(AttractionImage image) {
        images.remove(image);
        image.setAttraction(null);
    }
}
