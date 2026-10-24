package com.project.guide.controller;

import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.service.AttractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public read-only REST API for attractions.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Attractions", description = "South American attractions synchronized from Wikidata")
public class AttractionApiController {

    private final AttractionService attractionService;

    /**
     * GET /api/attractions?country=&search=
     */
    @GetMapping("/attractions")
    @Operation(summary = "List attractions", description = "Search by name takes precedence over the country filter")
    public List<AttractionDTO> getAttractions(
            @Parameter(description = "Exact country name, e.g. Peru") @RequestParam(required = false) String country,
            @Parameter(description = "Part of the attraction name, case-insensitive") @RequestParam(required = false) String search
    ) {
        if (search != null && !search.isEmpty()) {
            return attractionService.searchAttractions(search);
        }
        if (country != null && !country.isEmpty()) {
            return attractionService.getAttractionsByCountry(country);
        }
        return attractionService.getAllAttractions();
    }

    /**
     * GET /api/attractions/{id}
     */
    @GetMapping("/attractions/{id}")
    @Operation(summary = "Get attraction details", description = "Description is fetched from Wikipedia")
    public AttractionDetailDTO getAttraction(@PathVariable Long id) {
        return attractionService.getAttractionDetails(id);
    }

    /**
     * GET /api/countries
     */
    @GetMapping("/countries")
    @Operation(summary = "List countries that have attractions")
    public List<String> getCountries() {
        return attractionService.getAllCountries();
    }
}
