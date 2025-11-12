package com.project.guide.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing an image associated with a tourist attraction.
 */
@Entity
@Table(name = "attraction_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttractionImage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 1000)
    private String imageUrl;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attraction_id", nullable = false)
    private Attraction attraction;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttractionImage)) return false;
        return id != null && id.equals(((AttractionImage) o).id);
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
