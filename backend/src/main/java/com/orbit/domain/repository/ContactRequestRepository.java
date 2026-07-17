package com.orbit.domain.repository;

import java.util.Collection;
import java.util.List;

import com.orbit.domain.entity.ContactRequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {

    /** Postfach "Erhalten": contact requests addressed to the current user's entities. */
    List<ContactRequest> findByToEntityIdInOrderByCreatedAtDesc(Collection<Long> entityIds);

    /** Postfach "Gesendet": contact requests the current user's entities sent. */
    List<ContactRequest> findByFromEntityIdInOrderByCreatedAtDesc(Collection<Long> entityIds);

    /** Recipient entity ids the current user has already contacted (card indicators). */
    @Query("select distinct c.toEntityId from ContactRequest c where c.fromEntityId in :fromEntityIds")
    List<Long> findContactedEntityIds(Collection<Long> fromEntityIds);

    /** Whether any contact was already brokered for this request (first-contact check). */
    boolean existsByRequestId(Long requestId);
}
