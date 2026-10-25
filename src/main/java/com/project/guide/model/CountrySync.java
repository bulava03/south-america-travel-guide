package com.project.guide.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Result of the latest Wikidata sync of a country.
 */
@Entity
@Table(name = "country_syncs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountrySync {

    @Id
    @Column(length = 100)
    private String country;

    @Column(nullable = false)
    private LocalDateTime syncedAt;

    @Column(nullable = false)
    private boolean succeeded;

    /**
     * Attractions fetched for the country, 0 if the sync failed.
     */
    @Column(nullable = false)
    private int placeCount;

    /**
     * Error of a failed sync.
     */
    @Column(length = 500)
    private String message;
}
