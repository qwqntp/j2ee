package com.j2ee.tour.service;

import com.j2ee.tour.repository.AdminPaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminPaymentQueryServiceTest {
    @Test
    void rejectsReversedDateRange() {
        var service = new AdminPaymentQueryService(mock(AdminPaymentRepository.class));
        assertThrows(RuntimeException.class, () -> service.list(PageRequest.of(0, 10),
                null, null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null));
    }

    @Test
    void capsPageSizeAndUsesNewestFirst() {
        var repository = mock(AdminPaymentRepository.class);
        when(repository.search(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());
        var service = new AdminPaymentQueryService(repository);
        service.list(PageRequest.of(0, 100), null, null, null, null, null);
        verify(repository).search(isNull(), isNull(), isNull(), isNull(), isNull(),
                argThat(page -> page.getPageSize() == 50 && page.getSort().getOrderFor("paymentCreatedAt").isDescending()));
    }
}
