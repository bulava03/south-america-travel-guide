package com.project.guide.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String country;
    private Double latitude;
    private Double longitude;
    private String mainImage;
}
