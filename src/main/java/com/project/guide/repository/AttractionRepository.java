package com.project.guide.repository;

import com.project.guide.model.Attraction;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Attraction entity.
 * Lists are sorted by popularity, countries are fetched in the same query.
 * Queries for the site skip hidden attractions; the admin panel sees all of them.
 */
@Repository
public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    /**
     * Finds an attraction by its Wikidata URI.
     */
    Optional<Attraction> findByWikidataUri(String wikidataUri);

    /**
     * Finds all attractions including hidden ones, most popular first.
     */
    @EntityGraph(attributePaths = "countries")
    List<Attraction> findAllByOrderByPopularityDesc();

    /**
     * Finds visible attractions, most popular first.
     */
    @EntityGraph(attributePaths = "countries")
    List<Attraction> findByHiddenFalseOrderByPopularityDesc();

    /**
     * Finds the most popular visible attractions; countries are loaded lazily.
     */
    List<Attraction> findByHiddenFalseOrderByPopularityDesc(Limit limit);

    /**
     * Finds visible attractions located in a specific country.
     */
    @EntityGraph(attributePaths = "countries")
    @Query("SELECT a FROM Attraction a WHERE :country MEMBER OF a.countries AND a.hidden = false ORDER BY a.popularity DESC")
    List<Attraction> findByCountry(@Param("country") String country);

    /**
     * Searches visible attractions by name (case-insensitive).
     */
    @EntityGraph(attributePaths = "countries")
    @Query("SELECT a FROM Attraction a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND a.hidden = false ORDER BY a.popularity DESC")
    List<Attraction> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Gets all distinct countries of visible attractions.
     */
    @Query("SELECT DISTINCT c FROM Attraction a JOIN a.countries c WHERE a.hidden = false ORDER BY c")
    List<String> findAllDistinctCountries();

    long countByHiddenTrue();

    long countByMainImageIsNull();

    /**
     * Wikidata URIs of hidden attractions, skipped by the sync.
     */
    @Query("SELECT a.wikidataUri FROM Attraction a WHERE a.hidden = true")
    List<String> findHiddenWikidataUris();
}
