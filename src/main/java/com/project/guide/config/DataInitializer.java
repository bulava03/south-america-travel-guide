package com.project.guide.config;

import com.project.guide.service.AttractionService;
import com.project.guide.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes data on application startup.
 * Starts a background sync with Wikidata if database is empty; its progress is shown in the admin panel.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    private final AttractionService attractionService;
    private final SyncService syncService;
    
    @Value("${app.init.data-on-startup:true}")
    private boolean loadDataOnStartup;
    
    @Override
    public void run(String... args) {
        if (!loadDataOnStartup) {
            log.info("Data initialization disabled");
            return;
        }
        
        try {
            var stats = attractionService.getStatistics();
            long totalAttractions = (long) stats.get("totalAttractions");
            
            if (totalAttractions == 0) {
                log.info("Database is empty. Starting initial data load from Wikidata in the background");
                syncService.startAll();
            } else {
                log.info("Database already contains {} attractions. Skipping initial load.", totalAttractions);
            }
        } catch (Exception e) {
            log.error("Failed to initialize data", e);
            log.warn("Application will start with empty database. Use the admin panel to load data.");
        }
    }
}
