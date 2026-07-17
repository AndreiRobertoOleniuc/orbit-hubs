package com.orbit.app.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.orbit.app.search.ScopeResolver;
import com.orbit.domain.entity.DiscoveryEntity;
import com.orbit.domain.entity.EntityTag;
import com.orbit.domain.entity.Hub;
import com.orbit.domain.entity.HubMembership;
import com.orbit.domain.entity.Region;
import com.orbit.domain.entity.Request;
import com.orbit.domain.entity.RequestDistribution;
import com.orbit.domain.entity.RequestTag;
import com.orbit.domain.entity.Tag;
import com.orbit.domain.entity.TagSubscription;
import com.orbit.domain.repository.DiscoveryEntityRepository;
import com.orbit.domain.repository.EntityTagRepository;
import com.orbit.domain.repository.HubMembershipRepository;
import com.orbit.domain.repository.HubRepository;
import com.orbit.domain.repository.RegionRepository;
import com.orbit.domain.repository.RequestDistributionRepository;
import com.orbit.domain.repository.RequestRepository;
import com.orbit.domain.repository.RequestTagRepository;
import com.orbit.domain.repository.TagRepository;
import com.orbit.domain.repository.TagSubscriptionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the tag-based distribution (§8.2) and the widening ladder
 * (§8.3) — mocked repositories, no Spring context or DB.
 * <p>
 * Fixture geography: CH (region 1) ⊃ Aargau (region 2) ⊃ Region Baden
 * (region 3); hub FHNW (10) sits in Region Baden.
 */
class DistributionServiceTest {

    private static final long CH = 1, AARGAU = 2, BADEN = 3, FHNW = 10;
    private static final long TAG_EMBEDDED = 100, TAG_ZEPHYR = 101;

    private final RequestRepository requestRepository = mock(RequestRepository.class);
    private final RequestTagRepository requestTagRepository = mock(RequestTagRepository.class);
    private final RequestDistributionRepository distributionRepository = mock(RequestDistributionRepository.class);
    private final EntityTagRepository entityTagRepository = mock(EntityTagRepository.class);
    private final TagSubscriptionRepository tagSubscriptionRepository = mock(TagSubscriptionRepository.class);
    private final HubMembershipRepository hubMembershipRepository = mock(HubMembershipRepository.class);
    private final DiscoveryEntityRepository entityRepository = mock(DiscoveryEntityRepository.class);
    private final TagRepository tagRepository = mock(TagRepository.class);
    private final HubRepository hubRepository = mock(HubRepository.class);
    private final RegionRepository regionRepository = mock(RegionRepository.class);

    private DistributionService service;
    private final List<RequestDistribution> savedDistributions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Region ch = withId(new Region("ch", "Schweiz", null), CH);
        Region aargau = withId(new Region("aargau", "Aargau", CH), AARGAU);
        Region baden = withId(new Region("baden", "Region Baden", AARGAU), BADEN);
        Hub fhnw = withId(new Hub("fhnw", "FHNW", "university", BADEN), FHNW);

        when(regionRepository.findAll()).thenReturn(List.of(ch, aargau, baden));
        when(regionRepository.findById(CH)).thenReturn(Optional.of(ch));
        when(regionRepository.findById(AARGAU)).thenReturn(Optional.of(aargau));
        when(regionRepository.findById(BADEN)).thenReturn(Optional.of(baden));
        when(hubRepository.findById(FHNW)).thenReturn(Optional.of(fhnw));
        when(hubRepository.findByRegionIdIn(any())).thenAnswer(inv -> {
            List<Long> regionIds = inv.getArgument(0);
            return regionIds.contains(BADEN) ? List.of(fhnw) : List.<Hub>of();
        });

        when(tagRepository.findAllById(any())).thenReturn(List.of(
                withId(new Tag("embedded", "Embedded"), TAG_EMBEDDED),
                withId(new Tag("zephyr", "Zephyr"), TAG_ZEPHYR)));

        // Defaults — individual tests override what they need.
        when(entityTagRepository.findByTagIdIn(anyCollection())).thenReturn(List.of());
        when(tagSubscriptionRepository.findByTagIdIn(anyCollection())).thenReturn(List.of());
        when(hubMembershipRepository.findByEntityIdIn(anyCollection())).thenReturn(List.of());
        when(entityRepository.findByOwnerIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(distributionRepository.findByRequestId(any())).thenReturn(List.of());

        savedDistributions.clear();
        when(distributionRepository.save(any())).thenAnswer(inv -> {
            RequestDistribution d = inv.getArgument(0);
            savedDistributions.add(d);
            return d;
        });
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new DistributionService(requestRepository, requestTagRepository,
                distributionRepository, entityTagRepository, tagSubscriptionRepository,
                hubMembershipRepository, entityRepository, tagRepository,
                hubRepository, regionRepository,
                new ScopeResolver(regionRepository, hubRepository));
    }

    // ─── Cohort building ───

    @Test
    void tagOverlapNotifiesWithProfileMatchReason() {
        Request request = request("user_a", "all", null, null);
        givenRequestTags(request, TAG_EMBEDDED);
        givenEntityWithTags(5L, null, TAG_EMBEDDED);

        int notified = service.distribute(request);

        assertThat(notified).isEqualTo(1);
        assertThat(savedDistributions).hasSize(1);
        RequestDistribution d = savedDistributions.get(0);
        assertThat(d.getEntityId()).isEqualTo(5L);
        assertThat(d.getReason()).isEqualTo("profile_match");
        assertThat(d.getMatchedTags()).containsExactly("Embedded");
        assertThat(d.getWideningLevel()).isEqualTo("all");
    }

    @Test
    void subscriberIsNotifiedWithSubscriptionReason() {
        Request request = request("user_a", "all", null, null);
        givenRequestTags(request, TAG_ZEPHYR);
        DiscoveryEntity subscriber = entity(6L, null);
        when(tagSubscriptionRepository.findByTagIdIn(anyCollection()))
                .thenReturn(List.of(new TagSubscription(6L, TAG_ZEPHYR)));
        when(entityRepository.findAllById(anyCollection())).thenReturn(List.of(subscriber));

        service.distribute(request);

        assertThat(savedDistributions).hasSize(1);
        assertThat(savedDistributions.get(0).getReason()).isEqualTo("subscription");
        assertThat(savedDistributions.get(0).getMatchedTags()).containsExactly("Zephyr");
    }

    @Test
    void profileMatchWinsOverSubscription() {
        Request request = request("user_a", "all", null, null);
        givenRequestTags(request, TAG_EMBEDDED);
        givenEntityWithTags(7L, null, TAG_EMBEDDED);
        when(tagSubscriptionRepository.findByTagIdIn(anyCollection()))
                .thenReturn(List.of(new TagSubscription(7L, TAG_EMBEDDED)));

        service.distribute(request);

        assertThat(savedDistributions).hasSize(1);
        assertThat(savedDistributions.get(0).getReason()).isEqualTo("profile_match");
    }

    @Test
    void authorsOwnEntityIsNeverNotified() {
        Request request = request("user_a", "all", null, null);
        givenRequestTags(request, TAG_EMBEDDED);
        DiscoveryEntity own = givenEntityWithTags(8L, null, TAG_EMBEDDED);
        when(entityRepository.findByOwnerIdOrderByCreatedAtDesc("user_a")).thenReturn(List.of(own));

        assertThat(service.distribute(request)).isZero();
        assertThat(savedDistributions).isEmpty();
    }

    @Test
    void requestWithoutTagsStillTransitionsButNotifiesNobody() {
        Request request = request("user_a", "all", null, null);
        when(requestTagRepository.findByRequestId(any())).thenReturn(List.of());

        assertThat(service.distribute(request)).isZero();
        assertThat(request.getStatus()).isEqualTo("distributed");
        assertThat(request.getDistributedAt()).isNotNull();
        assertThat(request.getLastWaveAt()).isNotNull();
    }

    // ─── Scope filtering ───

    @Test
    void hubScopeKeepsOnlyHubMembers() {
        Request request = request("user_a", "hub", FHNW, null);
        givenRequestTags(request, TAG_EMBEDDED);
        DiscoveryEntity member = entity(5L, null);
        DiscoveryEntity outsider = entity(6L, null);
        when(entityTagRepository.findByTagIdIn(anyCollection())).thenReturn(List.of(
                new EntityTag(5L, TAG_EMBEDDED, "derived"),
                new EntityTag(6L, TAG_EMBEDDED, "derived")));
        when(entityRepository.findAllById(anyCollection())).thenReturn(List.of(member, outsider));
        when(hubMembershipRepository.findByEntityIdIn(anyCollection()))
                .thenReturn(List.of(new HubMembership(5L, FHNW, null, "confirmed")));

        service.distribute(request);

        assertThat(savedDistributions).extracting(RequestDistribution::getEntityId).containsExactly(5L);
        assertThat(savedDistributions.get(0).getWideningLevel()).isEqualTo("hub");
    }

    @Test
    void regionScopeIncludesHomeRegionInSubtree() {
        Request request = request("user_a", "region", null, AARGAU);
        givenRequestTags(request, TAG_EMBEDDED);
        givenEntityWithTags(5L, BADEN, TAG_EMBEDDED); // home region inside subtree

        service.distribute(request);

        assertThat(savedDistributions).hasSize(1);
        assertThat(savedDistributions.get(0).getWideningLevel()).isEqualTo("region");
    }

    @Test
    void inactiveEntitiesAreSkipped() {
        Request request = request("user_a", "all", null, null);
        givenRequestTags(request, TAG_EMBEDDED);
        DiscoveryEntity inactive = givenEntityWithTags(5L, null, TAG_EMBEDDED);
        setField(inactive, "status", "inactive");

        assertThat(service.distribute(request)).isZero();
    }

    // ─── Wave idempotency ───

    @Test
    void alreadyNotifiedEntityIsNotReinserted() {
        Request request = request("user_a", "all", null, null);
        givenRequestTags(request, TAG_EMBEDDED);
        givenEntityWithTags(5L, null, TAG_EMBEDDED);
        when(distributionRepository.findByRequestId(any())).thenReturn(List.of(
                new RequestDistribution(request.getId(), 5L, "profile_match",
                        List.of("Embedded"), "hub", null)));

        assertThat(service.distribute(request)).isZero();
        assertThat(savedDistributions).isEmpty();
    }

    // ─── Widening ladder (§8.3) ───

    @Test
    void wideningLadderClimbsHubToRegionToParentToAll() {
        Request request = request("user_a", "hub", FHNW, null);

        assertThat(service.effectiveScope(request).kind()).isEqualTo("hub");

        request.setEscalationLevel(1); // hub → its region (Baden)
        assertThat(service.effectiveScope(request).kind()).isEqualTo("region");
        assertThat(service.effectiveScope(request).regionId()).isEqualTo(BADEN);

        request.setEscalationLevel(2); // Baden → Aargau
        assertThat(service.effectiveScope(request).regionId()).isEqualTo(AARGAU);

        request.setEscalationLevel(3); // Aargau → CH
        assertThat(service.effectiveScope(request).regionId()).isEqualTo(CH);

        request.setEscalationLevel(4); // CH is the root → all
        assertThat(service.effectiveScope(request).kind()).isEqualTo("all");
        assertThat(service.canWiden(request)).isFalse();
    }

    @Test
    void allScopeCannotWiden() {
        Request request = request("user_a", "all", null, null);
        assertThat(service.canWiden(request)).isFalse();
    }

    @Test
    void hubScopeCanWiden() {
        Request request = request("user_a", "hub", FHNW, null);
        assertThat(service.canWiden(request)).isTrue();
    }

    // ─── Fixtures ───

    private Request request(String createdBy, String scopeKind, Long hubId, Long regionId) {
        Request r = new Request(createdBy, "t", null, "q", scopeKind, hubId, regionId,
                "flexible", null, null, null, null);
        return withId(r, 1L);
    }

    private void givenRequestTags(Request request, long... tagIds) {
        List<RequestTag> tags = new ArrayList<>();
        for (long tagId : tagIds) tags.add(new RequestTag(request.getId(), tagId));
        when(requestTagRepository.findByRequestId(request.getId())).thenReturn(tags);
    }

    private DiscoveryEntity givenEntityWithTags(long entityId, Long homeRegionId, long... tagIds) {
        DiscoveryEntity e = entity(entityId, homeRegionId);
        List<EntityTag> entityTags = new ArrayList<>();
        for (long tagId : tagIds) entityTags.add(new EntityTag(entityId, tagId, "derived"));
        when(entityTagRepository.findByTagIdIn(anyCollection())).thenReturn(entityTags);
        when(entityRepository.findAllById(anyCollection())).thenReturn(List.of(e));
        return e;
    }

    private DiscoveryEntity entity(long id, Long homeRegionId) {
        DiscoveryEntity e = new DiscoveryEntity("person", "E" + id, null, null);
        e.setHomeRegionId(homeRegionId);
        return withId(e, id);
    }

    /** Ids are DB-generated with no setters — set them reflectively for fixtures. */
    private static <T> T withId(T entity, long id) {
        setField(entity, "id", id);
        return entity;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
