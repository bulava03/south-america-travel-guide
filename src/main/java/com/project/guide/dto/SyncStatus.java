package com.project.guide.dto;

import java.time.Instant;
import java.util.List;

/**
 * State of the Wikidata sync for the admin panel: the current or the latest run since startup,
 * and the latest saved result of every country.
 *
 * @param running   a sync is in progress
 * @param stage     WIKIDATA, PHOTOS or SAVING while running; null if no sync ran since startup
 * @param result    changes of the finished run; null while running or after an error
 * @param error     why the run failed; nothing was changed then
 * @param countries every country from wikidata.yml, sorted by name
 */
public record SyncStatus(
        boolean running,
        String stage,
        Instant startedAt,
        Instant finishedAt,
        SyncSummary result,
        String error,
        List<CountryStatus> countries
) {

    /**
     * @param step       step in the current or latest run (WAITING ... DONE, FAILED), null if not in it
     * @param placeCount attractions fetched in that run
     * @param message    error of that run
     * @param failedStep step at which the country failed in that run
     * @param lastSync   latest saved result, null if the country was never synced
     */
    public record CountryStatus(String name, String step, int placeCount, String message, String failedStep,
                                LastSync lastSync) {
    }

    public record LastSync(Instant syncedAt, boolean succeeded, int placeCount, String message) {
    }
}
