package com.project.guide.repository;

import com.project.guide.model.AttractionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for AttractionImage entity.
 */
@Repository
public interface AttractionImageRepository extends JpaRepository<AttractionImage, Long> {
}
