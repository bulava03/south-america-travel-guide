package com.project.guide.dto;

/**
 * Changes made by a sync.
 *
 * @param places  attractions fetched from Wikidata
 * @param created new attractions
 * @param removed attractions that Wikidata no longer returns
 */
public record SyncSummary(int places, int created, int removed) {
}
