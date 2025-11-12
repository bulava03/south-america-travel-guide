package com.project.guide.controller;

import com.project.guide.dto.AttractionDTO;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.service.AttractionService;
import com.project.guide.service.WikipediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Web Controller for serving HTML pages.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebController {
    
    private final AttractionService attractionService;
    private final WikipediaService wikipediaService;
    
    /**
     * Home page - lists all attractions with filters.
     */
    @GetMapping("/")
    public String home(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            Model model
    ) {
        List<AttractionDTO> attractions;
        
        if (search != null && !search.isEmpty()) {
            attractions = attractionService.searchAttractions(search);
            model.addAttribute("searchTerm", search);
        } else if (country != null && !country.isEmpty()) {
            attractions = attractionService.getAttractionsByCountry(country);
            model.addAttribute("selectedCountry", country);
        } else {
            attractions = attractionService.getAllAttractions();
        }
        
        model.addAttribute("attractions", attractions);
        model.addAttribute("countries", attractionService.getAllCountries());
        
        return "index";
    }
    
    /**
     * Attraction details page.
     */
    @GetMapping("/attraction/{id}")
    public String attractionDetails(@PathVariable Long id, Model model) {
        try {
            AttractionDetailDTO attraction = attractionService.getAttractionDetails(id);

            // Fetch Wikipedia description if URL exists
            if (attraction.getWikipediaUrl() != null && !attraction.getWikipediaUrl().isEmpty()) {
                String wikipediaExtract = wikipediaService.fetchWikipediaExtract(attraction.getWikipediaUrl());
                if (wikipediaExtract != null) {
                    attraction.setDescription(wikipediaExtract);
                }
            }

            model.addAttribute("attraction", attraction);
            return "detail";
        } catch (Exception e) {
            log.error("Error loading attraction details for id: {}", id, e);
            model.addAttribute("error", "Attraction not found");
            return "error";
        }
    }
    
    /**
     * Admin panel page.
     */
    @GetMapping("/admin")
    public String adminPanel(Model model) {
        model.addAttribute("stats", attractionService.getStatistics());
        return "admin";
    }
}
