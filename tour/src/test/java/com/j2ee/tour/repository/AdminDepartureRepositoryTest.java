package com.j2ee.tour.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminDepartureRepositoryTest {
    private final EntityManager em = mock(EntityManager.class);
    private final Query query = mock(Query.class, RETURNS_SELF);
    private final AdminDepartureRepository repository = new AdminDepartureRepository(em);

    @Test
    void mapsJoinedScalarsAndCountInOneQueryWithLiteralKeyword() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        var date = Timestamp.valueOf("2026-12-01 08:00:00");
        when(query.getResultList()).thenReturn(Collections.singletonList(
                new Object[]{4L, 2L, "Bay tour", date, date, "FULL", 20, 20, 0, 21L}));
        var result = repository.search(PageRequest.of(0, 10), true, "FULL", "50%_!", LocalDateTime.now());
        assertThat(result.getTotalElements()).isEqualTo(21);
        assertThat(result.getContent().getFirst().remaining()).isZero();
        assertThat(result.getContent().getFirst().tourName()).isEqualTo("Bay tour");
        verify(em, times(1)).createNativeQuery(contains("JOIN tours"));
        verify(query, times(1)).getResultList();
        verify(query).setParameter("keyword", "%50!%!_!!%");
    }

    @Test
    void emptyOrOutOfRangePagesKeepCountWithoutAnotherQuery() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(
                new Object[]{null, null, null, null, null, null, null, null, null, 12L}));
        var result = repository.search(PageRequest.of(9, 10), false, null, null, LocalDateTime.now());
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(12);
        verify(em).createNativeQuery(contains("start_date DESC"));
        verify(query, times(1)).getResultList();
    }
}
