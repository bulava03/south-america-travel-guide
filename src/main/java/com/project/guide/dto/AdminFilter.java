package com.project.guide.dto;

import com.project.guide.model.Attraction;

import java.util.function.Predicate;

/**
 * Filter of the admin panel's place list.
 */
public enum AdminFilter {
    ALL(attraction -> true),
    VISIBLE(attraction -> !attraction.isHidden()),
    HIDDEN(Attraction::isHidden),
    NO_PHOTO(attraction -> attraction.getMainImage() == null);

    private final Predicate<Attraction> condition;

    AdminFilter(Predicate<Attraction> condition) {
        this.condition = condition;
    }

    public boolean matches(Attraction attraction) {
        return condition.test(attraction);
    }
}
