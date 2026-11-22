package com.project.guide.controller;

import com.project.guide.dto.AdminStatistics;
import com.project.guide.dto.SyncStatus;
import com.project.guide.service.AttractionService;
import com.project.guide.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Admin API, available to the ADMIN role only (see SecurityConfig).
 * A sync runs in the background: POST starts it, GET /sync shows the progress.
 * Errors are returned as problem details by ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AttractionService attractionService;
    private final SyncService syncService;

    public record AttractionUpdate(boolean hidden) {
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncStatus syncAll() {
        syncService.startAll();
        return syncService.status();
    }

    @PostMapping("/sync/{country}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SyncStatus syncCountry(@PathVariable String country) {
        syncService.startCountry(country);
        return syncService.status();
    }

    @GetMapping("/sync")
    public SyncStatus syncStatus() {
        return syncService.status();
    }

    /**
     * Hides an attraction from the site or shows it again.
     */
    @PatchMapping("/attractions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateAttraction(@PathVariable Long id, @RequestBody AttractionUpdate update) {
        attractionService.setHidden(id, update.hidden());
    }

    @GetMapping("/stats")
    public AdminStatistics getStatistics() {
        return attractionService.getStatistics();
    }
}
