package com.project.guide.service;

import com.project.guide.dto.SyncSummary;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Progress of the current or the latest sync, kept in memory.
 * Written by the sync thread and read by admin panel requests, so all methods are synchronized.
 */
class SyncProgress implements SyncListener {

    enum Stage { WIKIDATA, PHOTOS, SAVING }

    /**
     * @param failedStep step at which a failed country stopped
     */
    record CountryProgress(CountryStep step, int placeCount, String message, CountryStep failedStep) {
    }

    /**
     * Immutable copy for a response.
     */
    record Snapshot(boolean running, Stage stage, Instant startedAt, Instant finishedAt,
                    SyncSummary result, String error, Map<String, CountryProgress> countries) {
    }

    private boolean running;
    private Stage stage;
    private Instant startedAt;
    private Instant finishedAt;
    private SyncSummary result;
    private String error;
    private final Map<String, CountryProgress> countries = new LinkedHashMap<>();

    /**
     * @return false if a sync is already running
     */
    synchronized boolean tryStart(List<String> countryNames) {
        if (running) {
            return false;
        }
        running = true;
        stage = Stage.WIKIDATA;
        startedAt = Instant.now();
        finishedAt = null;
        result = null;
        error = null;
        countries.clear();
        countryNames.forEach(name -> countries.put(name, new CountryProgress(CountryStep.WAITING, 0, null, null)));
        return true;
    }

    synchronized void stage(Stage stage) {
        this.stage = stage;
    }

    synchronized void finish(SyncSummary result) {
        this.result = result;
        stop();
    }

    synchronized void fail(String error) {
        this.error = error;
        stop();
    }

    private void stop() {
        running = false;
        finishedAt = Instant.now();
    }

    @Override
    public synchronized void countryStep(String country, CountryStep step) {
        countries.put(country, new CountryProgress(step, 0, null, null));
    }

    @Override
    public synchronized void countryDone(String country, int placeCount) {
        countries.put(country, new CountryProgress(CountryStep.DONE, placeCount, null, null));
    }

    @Override
    public synchronized void countryFailed(String country, String message) {
        CountryProgress current = countries.get(country);
        CountryStep failedStep = current == null ? null : current.step();
        countries.put(country, new CountryProgress(CountryStep.FAILED, 0, message, failedStep));
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(running, stage, startedAt, finishedAt, result, error, new LinkedHashMap<>(countries));
    }
}
