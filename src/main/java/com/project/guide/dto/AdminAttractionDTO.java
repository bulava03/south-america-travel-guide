package com.project.guide.dto;

import com.project.guide.model.Attraction;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Row of the admin panel: the card as it is shown on the site and data to judge it.
 */
@Getter
@AllArgsConstructor
public class AdminAttractionDTO {
    private final AttractionDTO card;
    private final int popularity;
    private final boolean hidden;
    private final Integer photoWidth;
    private final Integer photoHeight;

    /**
     * Whole photo for the preview, 1600 px wide.
     */
    public String getPhoto() {
        return DisplayFormat.thumbnail(card.getMainImage(), 1600);
    }

    public String getPhotoPage() {
        return DisplayFormat.commonsPage(card.getMainImage());
    }

    public static AdminAttractionDTO from(Attraction attraction) {
        return new AdminAttractionDTO(
                AttractionDTO.from(attraction),
                attraction.getPopularity(),
                attraction.isHidden(),
                attraction.getMainImageWidth(),
                attraction.getMainImageHeight()
        );
    }
}
