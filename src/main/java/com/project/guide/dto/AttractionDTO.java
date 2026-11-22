package com.project.guide.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private String image;
    /** Width of the original photo, null if unknown. */
    @JsonIgnore
    private Integer imageWidth;
    private PhotoLayout photoLayout;

    /**
     * Photo for a card, 960 px wide.
     */
    public String getThumbnail() {
        return DisplayFormat.thumbnail(image, imageWidth, 960);
    }
    
    /**
     * Photo for small cards, 250 px wide.
     */
    public String getSmallThumbnail() {
        return DisplayFormat.thumbnail(image, imageWidth, 250);
    }

    public static AttractionDTO from(Attraction attraction) {
        return new AttractionDTO(
                attraction.getId(),
                attraction.getName(),
                attraction.getType(),
                attraction.getCountries().stream().sorted().toList(),
                attraction.getLatitude(),
                attraction.getLongitude(),
                attraction.getImage(),
                attraction.getImageWidth(),
                PhotoLayout.of(attraction.getImage(), attraction.getImageWidth(), attraction.getImageHeight())
        );
    }
}
