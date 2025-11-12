package com.project.guide.dto;

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
    private String country;
    private Double latitude;
    private Double longitude;
    private String mainImage;
    private String description;
    private String wikipediaUrl;
    private String wikidataUri;
    private List<String> additionalImages;
}
