package com.project.guide.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a country is not in wikidata.yml.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UnknownCountryException extends RuntimeException {

    public UnknownCountryException(String country) {
        super("Unknown country: " + country);
    }
}
