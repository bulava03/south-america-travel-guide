package com.project.guide.controller;

import com.project.guide.dto.AdminFilter;
import com.project.guide.dto.AttractionDetailDTO;
import com.project.guide.service.AttractionService;
import com.project.guide.service.WikidataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/**
 * Web Controller for serving HTML pages.
 */
@Controller
@RequiredArgsConstructor
public class WebController {

    private static final int MORE_IN_COUNTRY = 3;

    private final AttractionService attractionService;
    private final WikidataService wikidataService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("countries", attractionService.getAllCountries());
        return "home";
    }

    /**
     * All attractions with country filter and search.
     */
    @GetMapping("/places")
    public String places(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search,
            Model model
    ) {
        model.addAttribute("attractions", attractionService.findAttractions(country, search));
        model.addAttribute("countries", attractionService.getAllCountries());
        model.addAttribute("selectedCountry", country);
        model.addAttribute("searchTerm", search);
        return "places";
    }

    /**
     * Attraction page; AttractionNotFoundException is rendered by error.html with status 404.
     */
    @GetMapping("/attraction/{id}")
    public String attractionDetails(@PathVariable Long id, Model model) {
        AttractionDetailDTO attraction = attractionService.getAttractionDetails(id);
        model.addAttribute("attraction", attraction);
        model.addAttribute("more", attractionService.findMoreInCountry(attraction, MORE_IN_COUNTRY));
        return "detail";
    }

    /**
     * Admin panel: statistics, sync (rendered by admin.js from /api/admin/sync) and all places.
     */
    @GetMapping("/admin")
    public String adminPanel(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "ALL") AdminFilter show,
            Principal principal,
            Model model
    ) {
        model.addAttribute("stats", attractionService.getStatistics());
        model.addAttribute("places", attractionService.findForAdmin(search, country, show));
        model.addAttribute("countries", wikidataService.countryNames());
        model.addAttribute("searchTerm", search);
        model.addAttribute("selectedCountry", country);
        model.addAttribute("show", show.name());
        model.addAttribute("username", principal.getName());
        return "admin";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
