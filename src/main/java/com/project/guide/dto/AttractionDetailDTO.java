package com.project.guide.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private PhotoLayout photoLayout;
    private String description;
    private String wikipediaUrl;
    private String wikidataUri;
    private List<String> additionalImages;

    /**
     * Photo for the attraction page, 1600 px wide.
     */
    public String getPhoto() {
        return DisplayFormat.thumbnail(mainImage, 1600);
    }

    @JsonIgnore
    public String getPhotoPage() {
        return DisplayFormat.commonsPage(mainImage);
    }
    
    @JsonIgnore
    public String getWikidataPage() {
        return DisplayFormat.wikidataPage(wikidataUri);
    }
    
    @JsonIgnore
    public String getMapUrl() {
        return DisplayFormat.mapUrl(latitude, longitude);
    }
    
    @JsonIgnore
    public String getLatitudeText() {
        return DisplayFormat.latitude(latitude);
    }

    @JsonIgnore
    public String getLongitudeText() {
        return DisplayFormat.longitude(longitude);
    }

    public static AttractionDetailDTO from(Attraction attraction) {
        return new AttractionDetailDTO(
                attraction.getId(),
                attraction.getName(),
                attraction.getType(),
                attraction.getCountries().stream().sorted().toList(),
                attraction.getLatitude(),
                attraction.getLongitude(),
                attraction.getMainImage(),
                PhotoLayout.of(attraction.getMainImage(), attraction.getMainImageWidth(), attraction.getMainImageHeight()),
                attraction.getDescription(),
                attraction.getWikipediaUrl(),
                attraction.getWikidataUri(),
                attraction.getImages().stream().map(AttractionImage::getImageUrl).toList()
        );
    }
}
