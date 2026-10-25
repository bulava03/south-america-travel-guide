package com.project.guide.service;

import com.project.guide.dto.SyncStatus;
import com.project.guide.dto.SyncSummary;
import com.project.guide.dto.WikidataAttraction;
import com.project.guide.dto.WikidataFetchResult;
import com.project.guide.exception.SyncInProgressException;
import com.project.guide.model.CountrySync;
import com.project.guide.repository.CountrySyncRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The background task is collected instead of run, so a test decides when the sync happens.
 */
@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private WikidataService wikidataService;

    @Mock
    private PhotoService photoService;

    @Mock
    private AttractionService attractionService;

    @Mock
    private CountrySyncRepository countrySyncRepository;

    private final List<Runnable> tasks = new ArrayList<>();

    private SyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new SyncService(wikidataService, photoService, attractionService, countrySyncRepository, tasks::add);
        lenient().when(wikidataService.countryNames()).thenReturn(List.of("Chile", "Peru"));
        lenient().when(attractionService.findHiddenWikidataUris()).thenReturn(Set.of("http://www.wikidata.org/entity/Q2"));
    }

    @Test
    void testSyncAllFetchesChecksPhotosAndSaves() {
        WikidataFetchResult result = new WikidataFetchResult(
                List.of(new WikidataAttraction()), Map.of("Peru", 1), Map.of("Chile", "timed out"));
        when(wikidataService.fetchAttractions(eq(List.of("Chile", "Peru")),
                eq(Set.of("http://www.wikidata.org/entity/Q2")), any())).thenReturn(result);
        when(attractionService.saveSyncResult(result)).thenReturn(new SyncSummary(1, 1, 0));

        syncService.startAll();
        assertTrue(syncService.status().running());
        runTasks();

        verify(photoService).selectPhotos(result.attractions());
        SyncStatus status = syncService.status();
        assertFalse(status.running());
        assertEquals(new SyncSummary(1, 1, 0), status.result());
        assertNull(status.error());
        assertNotNull(status.finishedAt());
    }

    @Test
    void testSecondSyncIsRejectedWhileRunning() {
        syncService.startAll();

        assertThrows(SyncInProgressException.class, () -> syncService.startCountry("Peru"));
        assertEquals(1, tasks.size());
    }

    @Test
    void testUnknownCountryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> syncService.startCountry("Mexico"));
        assertFalse(syncService.status().running());
    }

    @Test
    void testAllCountriesFailed() {
        WikidataFetchResult result = new WikidataFetchResult(List.of(), Map.of(), Map.of("Peru", "timed out"));
        when(wikidataService.fetchAttractions(eq(List.of("Peru")), any(), any())).thenReturn(result);

        syncService.startCountry("Peru");
        runTasks();

        // Country results are still saved, attractions stay as they were
        verify(attractionService).saveSyncResult(result);
        verify(photoService, never()).selectPhotos(any());
        SyncStatus status = syncService.status();
        assertFalse(status.running());
        assertNull(status.result());
        assertTrue(status.error().startsWith("Wikidata did not answer"));
    }

    @Test
    void testErrorWhileSavingEndsTheSync() {
        when(wikidataService.fetchAttractions(any(), any(), any()))
                .thenReturn(new WikidataFetchResult(List.of(), Map.of("Peru", 0), Map.of()));
        when(attractionService.saveSyncResult(any())).thenThrow(new IllegalStateException("database is down"));

        syncService.startCountry("Peru");
        runTasks();

        SyncStatus status = syncService.status();
        assertFalse(status.running());
        assertTrue(status.error().contains("database is down"));
        // The next sync can start
        syncService.startAll();
    }

    @Test
    void testStatusShowsProgressAndLatestResultOfEveryCountry() {
        when(countrySyncRepository.findAll()).thenReturn(List.of(
                new CountrySync("Chile", LocalDateTime.now(), false, 0, "timed out")));
        syncService.startCountry("Peru");

        SyncStatus status = syncService.status();

        assertEquals("WIKIDATA", status.stage());
        SyncStatus.CountryStatus chile = status.countries().get(0);
        SyncStatus.CountryStatus peru = status.countries().get(1);
        assertNull(chile.step());
        assertFalse(chile.lastSync().succeeded());
        assertEquals("timed out", chile.lastSync().message());
        assertEquals("WAITING", peru.step());
        assertNull(peru.lastSync());
    }

    private void runTasks() {
        List<Runnable> pending = List.copyOf(tasks);
        tasks.clear();
        pending.forEach(Runnable::run);
    }
}
