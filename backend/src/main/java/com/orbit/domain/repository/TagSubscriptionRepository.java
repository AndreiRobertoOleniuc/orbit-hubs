package com.orbit.domain.repository;

import java.util.List;

import com.orbit.domain.entity.TagSubscription;
import com.orbit.domain.entity.TagSubscriptionId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TagSubscriptionRepository extends JpaRepository<TagSubscription, TagSubscriptionId> {

    List<TagSubscription> findByEntityId(Long entityId);
}
