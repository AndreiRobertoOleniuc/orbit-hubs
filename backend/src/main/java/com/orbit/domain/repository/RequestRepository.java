package com.orbit.domain.repository;

import java.util.List;

import com.orbit.domain.entity.Request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestRepository extends JpaRepository<Request, Long> {

    /** "My Requests" — all requests created by a user, sorted by creation. */
    List<Request> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /** Public board — all open requests, newest first. */
    List<Request> findByStatusOrderByCreatedAtDesc(String status);
}