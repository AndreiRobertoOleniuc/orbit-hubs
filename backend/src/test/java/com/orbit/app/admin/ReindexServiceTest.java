package com.orbit.app.admin;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.orbit.app.auth.CurrentUser;
import com.orbit.app.controllers.dto.ReindexDto;
import com.orbit.app.controllers.dto.ReindexRequest;
import com.orbit.app.ingestion.job.IngestionJobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReindexServiceTest {

    private IngestionJobStore jobs;
    private CurrentUser currentUser;
    private ReindexService service;

    @BeforeEach
    void setUp() {
        jobs = mock(IngestionJobStore.class);
        currentUser = mock(CurrentUser.class);
        service = new ReindexService(jobs, new AdminAccess("admin-1"), currentUser);
    }

    @Test
    void anAdminQueuesEveryEntityAndGetsTheProgress() {
        when(currentUser.userId()).thenReturn(Optional.of("admin-1"));
        when(jobs.enqueueResyncs(null)).thenReturn(7);
        when(jobs.resyncCounts()).thenReturn(Map.of("queued", 7L, "succeeded", 3L));

        assertThat(service.reindex(null)).isEqualTo(new ReindexDto(7, 7, 0, 3, 0));
    }

    @Test
    void selectedEntitiesArePassedOn() {
        when(currentUser.userId()).thenReturn(Optional.of("admin-1"));
        when(jobs.enqueueResyncs(List.of(4L, 9L))).thenReturn(2);
        when(jobs.resyncCounts()).thenReturn(Map.of());

        assertThat(service.reindex(new ReindexRequest(List.of(4L, 9L))).queued()).isEqualTo(2);
    }

    @Test
    void anyoneElseIsRefusedAndNothingIsQueued() {
        when(currentUser.userId()).thenReturn(Optional.of("someone"));

        assertThatThrownBy(() -> service.reindex(null)).isInstanceOf(AdminAccessDeniedException.class);
        assertThatThrownBy(() -> service.status()).isInstanceOf(AdminAccessDeniedException.class);
        verify(jobs, never()).enqueueResyncs(any());
    }
}
