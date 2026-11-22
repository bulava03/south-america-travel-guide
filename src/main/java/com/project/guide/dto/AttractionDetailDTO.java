package com.project.guide.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.project.guide.model.Attraction;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Attraction page: the card data plus the description and links.
 * In JSON the card fields stay on the top level.
 */
@Getter
@AllArgsConstructor
public class AttractionDetailDTO {
    @JsonUnwrapped
    private final AttractionDTO card;
    private final String description;
    private final String wikipediaUrl;
    private final String wikidataUri;

    /**
     * Photo for the attraction page, 1600 px wide.
     */
    public String getPhoto() {
        return DisplayFormat.thumbnail(card.getImage(), 1600);
    }

    @JsonIgnore
    public String getPhotoPage() {
        return DisplayFormat.commonsPage(card.getImage());
    }

    @JsonIgnore
    public String getWikidataPage() {
        return DisplayFormat.wikidataPage(wikidataUri);
    }

    @JsonIgnore
    public String getMapUrl() {
        return DisplayFormat.mapUrl(card.getLatitude(), card.getLongitude());
    }

    @JsonIgnore
    public String getLatitudeText() {
        return DisplayFormat.latitude(card.getLatitude());
    }

    @JsonIgnore
    public String getLongitudeText() {
        return DisplayFormat.longitude(card.getLongitude());
    }

    public static AttractionDetailDTO from(Attraction attraction) {
        return new AttractionDetailDTO(
                AttractionDTO.from(attraction),
                attraction.getDescription(),
                attraction.getWikipediaUrl(),
                attraction.getWikidataUri()
        );
    }
}
