package com.project.guide.controller;

import com.project.guide.dto.SyncStatus;
import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.exception.SyncInProgressException;
import com.project.guide.service.AttractionService;
import com.project.guide.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin API, available to the ADMIN role only (see SecurityConfig).
 * A sync runs in the background: POST starts it, GET /sync shows the progress.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
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
    public Map<String, Object> getStatistics() {
        return attractionService.getStatistics();
    }

    @ExceptionHandler(SyncInProgressException.class)
    public ResponseEntity<Map<String, String>> handleSyncInProgress(SyncInProgressException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AttractionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AttractionNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException e) {
        log.error("Admin operation failed", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", "error", "message", String.valueOf(message)));
    }
}
