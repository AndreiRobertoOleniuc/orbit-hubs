package com.orbit.domain.repository;

import java.util.List;

import com.orbit.domain.entity.RequestTag;
import com.orbit.domain.entity.RequestTagId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestTagRepository extends JpaRepository<RequestTag, RequestTagId> {

    /** All tag IDs attached to a request. */
    List<RequestTag> findByRequestId(Long requestId);

    /** Delete all tags for a request (used when re-setting tags on edit). */
    void deleteByRequestId(Long requestId);
}