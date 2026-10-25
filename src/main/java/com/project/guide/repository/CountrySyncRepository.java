package com.project.guide.repository;

import com.project.guide.model.CountrySync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for CountrySync entity, keyed by country name.
 */
@Repository
public interface CountrySyncRepository extends JpaRepository<CountrySync, String> {
}
