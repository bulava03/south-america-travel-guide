package com.project.guide.repository;

import com.project.guide.model.Attraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Attraction entity.
 */
@Repository
public interface AttractionRepository extends JpaRepository<Attraction, Long> {
    
    /**
     * Finds an attraction by its Wikidata URI.
     */
    Optional<Attraction> findByWikidataUri(String wikidataUri);
    
    /**
     * Finds all attractions in a specific country.
     */
    List<Attraction> findByCountry(String country);
    
    /**
     * Finds all attractions of a specific type.
     */
    List<Attraction> findByType(String type);
    
    /**
     * Finds all attractions by country and type.
     */
    List<Attraction> findByCountryAndType(String country, String type);
    
    /**
     * Searches attractions by name (case-insensitive).
     */
    @Query("SELECT a FROM Attraction a WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Attraction> searchByName(@Param("searchTerm") String searchTerm);
    
    /**
     * Gets all distinct countries.
     */
    @Query("SELECT DISTINCT a.country FROM Attraction a ORDER BY a.country")
    List<String> findAllDistinctCountries();
    
    /**
     * Gets all distinct types.
     */
    @Query("SELECT DISTINCT a.type FROM Attraction a ORDER BY a.type")
    List<String> findAllDistinctTypes();
    
    /**
     * Counts attractions by country.
     */
    @Query("SELECT COUNT(a) FROM Attraction a WHERE a.country = :country")
    long countByCountry(@Param("country") String country);
}
