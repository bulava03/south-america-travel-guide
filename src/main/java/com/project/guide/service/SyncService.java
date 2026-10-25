package com.project.guide.service;

import com.project.guide.dto.SyncStatus;
import com.project.guide.dto.SyncStatus.CountryStatus;
import com.project.guide.dto.SyncStatus.LastSync;
import com.project.guide.dto.SyncSummary;
import com.project.guide.dto.WikidataFetchResult;
import com.project.guide.exception.SyncInProgressException;
import com.project.guide.model.CountrySync;
import com.project.guide.repository.CountrySyncRepository;
import com.project.guide.service.SyncProgress.CountryProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs the Wikidata sync in the background, for all countries or for one of them:
 * Wikidata -> photos on Commons -> saving. Only one sync runs at a time;
 * the admin panel polls {@link #status()} to show the progress.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private final WikidataService wikidataService;
    private final PhotoService photoService;
    private final AttractionService attractionService;
    private final CountrySyncRepository countrySyncRepository;
    private final TaskExecutor taskExecutor;
    private final SyncProgress progress = new SyncProgress();

    /**
     * @throws SyncInProgressException if a sync is already running
     */
    public void startAll() {
        start(wikidataService.countryNames());
    }

    /**
     * @throws IllegalArgumentException if the country is not in wikidata.yml
     * @throws SyncInProgressException  if a sync is already running
     */
    public void startCountry(String country) {
        if (!wikidataService.countryNames().contains(country)) {
            throw new IllegalArgumentException("Unknown country: " + country);
        }
        start(List.of(country));
    }

    private void start(List<String> countries) {
        if (!progress.tryStart(countries)) {
            throw new SyncInProgressException();
        }
        log.info("Starting sync of {}", countries);
        try {
            taskExecutor.execute(() -> run(countries));
        } catch (RuntimeException e) {
            progress.fail("Could not start the sync: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Data stays as it was if Wikidata failed for all countries or anything else went wrong.
     */
    private void run(List<String> countries) {
        try {
            Set<String> hiddenUris = attractionService.findHiddenWikidataUris();
            WikidataFetchResult result = wikidataService.fetchAttractions(countries, hiddenUris, progress);
            if (!result.isEmpty()) {
                progress.stage(SyncProgress.Stage.PHOTOS);
                photoService.selectPhotos(result.attractions());
            }

            progress.stage(SyncProgress.Stage.SAVING);
            SyncSummary summary = attractionService.saveSyncResult(result);
            if (result.isEmpty()) {
                progress.fail("Wikidata did not answer for any country. Nothing was changed, try again later.");
            } else {
                progress.finish(summary);
            }
        } catch (RuntimeException e) {
            log.error("Sync failed", e);
            progress.fail("The sync failed: " + e.getMessage() + ". Nothing was changed.");
        }
    }

    public SyncStatus status() {
        SyncProgress.Snapshot snapshot = progress.snapshot();
        Map<String, CountrySync> lastSyncs = countrySyncRepository.findAll().stream()
                .collect(Collectors.toMap(CountrySync::getCountry, Function.identity()));

        List<CountryStatus> countries = wikidataService.countryNames().stream()
                .map(name -> countryStatus(name, snapshot.countries().get(name), lastSyncs.get(name)))
                .toList();
        return new SyncStatus(
                snapshot.running(),
                snapshot.stage() == null ? null : snapshot.stage().name(),
                snapshot.startedAt(),
                snapshot.finishedAt(),
                snapshot.result(),
                snapshot.error(),
                countries
        );
    }

    private static CountryStatus countryStatus(String name, CountryProgress current, CountrySync last) {
        LastSync lastSync = last == null ? null : new LastSync(
                last.getSyncedAt().atZone(ZoneId.systemDefault()).toInstant(),
                last.isSucceeded(), last.getPlaceCount(), last.getMessage());
        if (current == null) {
            return new CountryStatus(name, null, 0, null, null, lastSync);
        }
        return new CountryStatus(name, current.step().name(), current.placeCount(), current.message(),
                current.failedStep() == null ? null : current.failedStep().name(), lastSync);
    }
}
