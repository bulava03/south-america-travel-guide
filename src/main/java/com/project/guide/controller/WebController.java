package com.project.guide.controller;

import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.service.AttractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Web Controller for serving HTML pages.
 */
@Controller
@RequiredArgsConstructor
public class WebController {
    
    private final AttractionService attractionService;
    
    /**
     * Home page - lists all attractions with filters.
     */
    @GetMapping("/")
    public String home(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            Model model
    ) {
        model.addAttribute("attractions", attractionService.findAttractions(country, search));
        model.addAttribute("countries", attractionService.getAllCountries());
        model.addAttribute("selectedCountry", country);
        model.addAttribute("searchTerm", search);
        return "index";
    }
    
    /**
     * Attraction details page.
     */
    @GetMapping("/attraction/{id}")
    public String attractionDetails(@PathVariable Long id, Model model) {
        // AttractionNotFoundException is rendered by error.html with status 404
        AttractionDetailDTO attraction = attractionService.getAttractionDetails(id);
        model.addAttribute("attraction", attraction);
        return "detail";
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
