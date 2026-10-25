package com.project.guide.dto;

import com.project.guide.model.Attraction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for Attraction list view (lightweight).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttractionDTO {
    private Long id;
    private String name;
    private String type;
    private List<String> countries;
    private Double latitude;
    private Double longitude;
    private String mainImage;
    private PhotoLayout photoLayout;

    /**
     * Photo for a card, 800 px wide.
     */
    public String getThumbnail() {
        return DisplayFormat.thumbnail(mainImage, 800);
    }
    
    /**
     * Photo for small cards, 200 px wide.
     */
    public String getSmallThumbnail() {
        return DisplayFormat.thumbnail(mainImage, 200);
    }

    public static AttractionDTO from(Attraction attraction) {
        return new AttractionDTO(
                attraction.getId(),
                attraction.getName(),
                attraction.getType(),
                attraction.getCountries().stream().sorted().toList(),
                attraction.getLatitude(),
                attraction.getLongitude(),
                attraction.getMainImage(),
                PhotoLayout.of(attraction.getMainImage(), attraction.getMainImageWidth(), attraction.getMainImageHeight())
        );
    }
}
