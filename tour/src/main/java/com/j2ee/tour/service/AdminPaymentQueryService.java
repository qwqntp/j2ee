package com.j2ee.tour.service;

import com.j2ee.tour.common.ApiException;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.response.AdminPaymentItem;
import com.j2ee.tour.repository.AdminPaymentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminPaymentQueryService {
    private final AdminPaymentRepository repository;
    public AdminPaymentQueryService(AdminPaymentRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentItem> list(Pageable page, String status, String method,
                                               LocalDate from, LocalDate to, String q) {
        if (from != null && to != null && from.isAfter(to))
            throw new ApiException(HttpStatus.BAD_REQUEST, "from must be on or before to");
        status = normalize(status, Set.of("PENDING", "SUCCESS", "FAILED"));
        method = normalize(method, Set.of("CASH", "VNPAY", "MOMO", "CARD"));
        if (q != null) {
            q = q.trim().replace("%", "").replace("_", "");
            if (q.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Search is too long");
            if (q.isEmpty()) q = null;
        }
        Pageable effective = PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 50),
                Sort.by(Sort.Order.desc("paymentCreatedAt"), Sort.Order.desc("paymentId")));
        return PageResponse.from(repository.search(status, method, from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(), q, effective));
    }

    private String normalize(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid filter");
        return normalized;
    }
}
