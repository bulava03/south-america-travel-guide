package com.project.guide.controller;

import com.project.guide.service.AttractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin API, available to the ADMIN role only (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AttractionService attractionService;

    @PostMapping("/sync")
    public Map<String, String> syncWithWikidata() {
        attractionService.syncWithWikidata();
        return Map.of("status", "success", "message", "Successfully synchronized with Wikidata");
    }

    @GetMapping("/stats")
    public Map<String, Object> getStatistics() {
        return attractionService.getStatistics();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleError(RuntimeException e) {
        log.error("Admin operation failed", e);
        return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
    }
}
