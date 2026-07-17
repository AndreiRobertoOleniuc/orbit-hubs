package com.orbit.domain.repository;

import java.util.List;

import com.orbit.domain.entity.Request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {

    /** "My Requests" — all requests created by a user, sorted by creation. */
    List<Request> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /** Requests in one status, newest first (e.g. the escalation scheduler's 'distributed' scan). */
    List<Request> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Public board — every live request, newest first. Requests stay on the
     * board through their whole lifecycle (open → distributed → contacted) and
     * only drop off once closed (§8.1). Since distribution runs on create, no
     * request is ever 'open' for long, so filtering on a single status would
     * leave the board permanently empty.
     */
    List<Request> findByStatusNotOrderByCreatedAtDesc(String status);
}