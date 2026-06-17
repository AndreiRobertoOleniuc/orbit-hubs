package com.orbit.domain.repository;

import java.util.List;

import com.orbit.domain.entity.Document;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, String> {

    List<Document> findByEntity_Id(String entityId);
}
