package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.AdminCustomerBooking;
import com.j2ee.tour.dto.response.CustomerResponse;
import com.j2ee.tour.repository.AdminCustomerRepository;
import com.j2ee.tour.repository.CustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class AdminCustomerQueryService {
    private final AdminCustomerRepository repository;
    private final CustomerRepository customers;
    public AdminCustomerQueryService(AdminCustomerRepository repository, CustomerRepository customers) {
        this.repository = repository;
        this.customers = customers;
    }
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(Pageable page, String q) {
        q = q == null ? "" : q.trim().replace("%", "").replace("_", "");
        if (q.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Search is too long");
        return PageResponse.from(repository.search(q, limit(page)));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminCustomerBooking> bookings(Long id, Pageable page) {
        if (!customers.existsById(id)) throw new NoSuchElementException("Customer not found");
        return PageResponse.from(repository.bookings(id, limit(page)));
    }

    private Pageable limit(Pageable page) { return PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 50)); }
}
