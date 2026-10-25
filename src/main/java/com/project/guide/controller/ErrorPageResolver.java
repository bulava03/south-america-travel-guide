package com.project.guide.controller;

import com.project.guide.service.AttractionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders error.html for all errors of the web pages.
 * The 404 page also links to the most visited places. Other errors do not touch the database,
 * because the database itself may be the cause.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ErrorPageResolver implements ErrorViewResolver {

    private static final int MOST_VISITED = 4;

    private final AttractionService attractionService;

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        Map<String, Object> errorModel = new HashMap<>(model);
        errorModel.put("popular", status == HttpStatus.NOT_FOUND ? findMostVisited() : List.of());
        return new ModelAndView("error", errorModel, status);
    }

    private List<?> findMostVisited() {
        try {
            return attractionService.findMostVisited(MOST_VISITED);
        } catch (RuntimeException e) {
            log.warn("Could not load the most visited places for the error page", e);
            return List.of();
        }
    }
}
