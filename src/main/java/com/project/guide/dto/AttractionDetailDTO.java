package com.project.guide.dto;

import com.project.guide.model.Attraction;
import com.project.guide.model.AttractionImage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for Attraction detail view (complete information).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttractionDetailDTO {
    private Long id;
    private String name;
    private String type;
    private List<String> countries;
    private Double latitude;
    private Double longitude;
    private String mainImage;
    private String description;
    private String wikipediaUrl;
    private String wikidataUri;
    private List<String> additionalImages;

    public static AttractionDetailDTO from(Attraction attraction) {
        return new AttractionDetailDTO(
                attraction.getId(),
                attraction.getName(),
                attraction.getType(),
                attraction.getCountries().stream().sorted().toList(),
                attraction.getLatitude(),
                attraction.getLongitude(),
                attraction.getMainImage(),
                attraction.getDescription(),
                attraction.getWikipediaUrl(),
                attraction.getWikidataUri(),
                attraction.getImages().stream().map(AttractionImage::getImageUrl).toList()
        );
    }
}
