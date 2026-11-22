package com.project.guide.exception;

/**
 * Thrown when a country is not in wikidata.yml.
 */
public class UnknownCountryException extends RuntimeException {

    public UnknownCountryException(String country) {
        super("Unknown country: " + country);
    }
}
