package com.orbit.domain.repository;

import com.orbit.domain.entity.Request;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<Request, String> {
}
