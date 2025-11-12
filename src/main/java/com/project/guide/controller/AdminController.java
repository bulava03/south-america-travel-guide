package com.project.guide.controller;

import com.project.guide.service.AttractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin Controller for administrative operations.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class AdminController {
    
    private final AttractionService attractionService;
    
    /**
     * Triggers synchronization with Wikidata.
     * POST /api/admin/sync
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> syncWithWikidata() {
        try {
            log.info("Admin triggered Wikidata sync");
            attractionService.syncWithWikidata();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Successfully synchronized with Wikidata"
            ));
        } catch (Exception e) {
            log.error("Error during Wikidata sync", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to sync: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Clears all data from database.
     * POST /api/admin/clear
     */
    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clearAllData() {
        try {
            log.warn("Admin triggered data clear");
            attractionService.clearAllData();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Successfully cleared all data"
            ));
        } catch (Exception e) {
            log.error("Error clearing data", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to clear data: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Gets application statistics.
     * GET /api/admin/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = attractionService.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting statistics", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get statistics: " + e.getMessage()
            ));
        }
    }
}
