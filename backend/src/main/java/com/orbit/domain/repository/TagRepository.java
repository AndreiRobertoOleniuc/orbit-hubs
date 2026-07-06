package com.orbit.domain.repository;

import java.util.List;
import java.util.Optional;

import com.orbit.domain.entity.Tag;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /** Find a tag by its slug (the controlled-vocabulary lookup key). */
    Optional<Tag> findBySlug(String slug);

    /** Find all tags matching any of the given slugs — for bulk upsert. */
    List<Tag> findBySlugIn(List<String> slugs);
}