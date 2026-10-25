package com.project.guide.service;

/**
 * Receives the progress of a Wikidata sync, country by country.
 */
public interface SyncListener {

    SyncListener NONE = new SyncListener() {
    };

    /**
     * Steps of a country, in order; after a failed attempt they start again.
     */
    enum CountryStep { WAITING, SEARCH, CANDIDATES, DETAILS, DONE, FAILED }

    default void countryStep(String country, CountryStep step) {
    }

    default void countryDone(String country, int placeCount) {
    }

    default void countryFailed(String country, String message) {
    }
}
