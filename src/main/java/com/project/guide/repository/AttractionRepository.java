package com.project.guide.repository;

import com.project.guide.model.Attraction;
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
 */
@Repository
public interface AttractionRepository extends JpaRepository<Attraction, Long> {

    /**
     * Finds an attraction by its Wikidata URI.
     */
    Optional<Attraction> findByWikidataUri(String wikidataUri);

    /**
     * Finds all attractions, most popular first.
     */
    @EntityGraph(attributePaths = "countries")
    List<Attraction> findAllByOrderByPopularityDesc();

    /**
     * Finds all attractions located in a specific country.
     */
    @EntityGraph(attributePaths = "countries")
    @Query("SELECT a FROM Attraction a WHERE :country MEMBER OF a.countries ORDER BY a.popularity DESC")
    List<Attraction> findByCountry(@Param("country") String country);

    /**
     * Searches attractions by name (case-insensitive).
     */
    @EntityGraph(attributePaths = "countries")
    @Query("SELECT a FROM Attraction a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY a.popularity DESC")
    List<Attraction> searchByName(@Param("searchTerm") String searchTerm);

    /**
     * Gets all distinct countries.
     */
    @Query("SELECT DISTINCT c FROM Attraction a JOIN a.countries c ORDER BY c")
    List<String> findAllDistinctCountries();
}
