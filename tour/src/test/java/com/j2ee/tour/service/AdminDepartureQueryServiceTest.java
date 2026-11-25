package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.repository.AdminDepartureRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminDepartureQueryServiceTest {
    private final AdminDepartureRepository repository = mock(AdminDepartureRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T01:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    private final AdminDepartureQueryService service = new AdminDepartureQueryService(repository, clock, 50);

    @Test
    void capsPageSizeAndNormalizesFiltersWithoutLosingEmptyPageTotals() {
        var page = PageRequest.of(3, 50);
        when(repository.search(page, true, "FULL", "bay", LocalDateTime.now(clock)))
                .thenReturn(new PageImpl<>(List.of(), page, 12));
        var result = service.list(3, 500, true, "FULL", " bay ");
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(12);
        assertThat(result.size()).isEqualTo(50);
    }

    @Test
    void rejectsBadPagingStatusAndLongKeywordsBeforeQuerying() {
        assertThatThrownBy(() -> service.list(-1, 10, true, null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list(0, 0, true, null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list(0, 10, true, "PAID", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list(0, 10, true, null, "x".repeat(101))).isInstanceOf(ApiException.class);
        verifyNoInteractions(repository);
    }
}
