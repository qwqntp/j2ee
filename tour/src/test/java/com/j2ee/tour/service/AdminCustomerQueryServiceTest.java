package com.j2ee.tour.service;

import com.j2ee.tour.repository.AdminCustomerRepository;
import com.j2ee.tour.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminCustomerQueryServiceTest {
    @Test
    void stripsWildcardsAndCapsPageSize() {
        var repository = mock(AdminCustomerRepository.class);
        when(repository.search(anyString(), any())).thenReturn(Page.empty());
        var service = new AdminCustomerQueryService(repository, mock(CustomerRepository.class));
        service.list(PageRequest.of(0, 100), "  a%b_  ");
        verify(repository).search(eq("ab"), argThat(page -> page.getPageSize() == 50));
    }

    @Test
    void rejectsOverlongSearch() {
        var service = new AdminCustomerQueryService(mock(AdminCustomerRepository.class), mock(CustomerRepository.class));
        assertThrows(RuntimeException.class, () -> service.list(PageRequest.of(0, 10), "a".repeat(101)));
    }
}
