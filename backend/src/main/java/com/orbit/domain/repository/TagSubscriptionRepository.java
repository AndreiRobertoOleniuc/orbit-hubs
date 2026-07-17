package com.orbit.domain.repository;

import java.util.Collection;
import java.util.List;

import com.orbit.domain.entity.TagSubscription;
import com.orbit.domain.entity.TagSubscriptionId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TagSubscriptionRepository extends JpaRepository<TagSubscription, TagSubscriptionId> {

    List<TagSubscription> findByEntityId(Long entityId);

    /** Reverse lookup for distribution (§8.2): all subscribers of any of these tags. */
    List<TagSubscription> findByTagIdIn(Collection<Long> tagIds);
}
