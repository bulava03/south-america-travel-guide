package com.project.guide.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an attraction with the given id does not exist.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AttractionNotFoundException extends RuntimeException {

    public AttractionNotFoundException(Long id) {
        super("Attraction not found with id: " + id);
    }
}
