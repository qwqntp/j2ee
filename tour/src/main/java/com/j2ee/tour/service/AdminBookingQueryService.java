package com.j2ee.tour.service;

import com.j2ee.tour.common.AdminListPageRequest;
import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.AdminBookingFilter;
import com.j2ee.tour.dto.response.AdminBookingListItem;
import com.j2ee.tour.model.Booking;
import com.j2ee.tour.repository.AdminBookingListRepository;
import com.j2ee.tour.repository.BookingSpecifications;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AdminBookingQueryService {
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "bookingCreatedAt",
            "totalAmount", "bookingTotalAmount",
            "id", "bookingId");

    private final AdminBookingListRepository adminBookingListRepository;
    private final int maxPageSize;

    public AdminBookingQueryService(AdminBookingListRepository adminBookingListRepository,
                                    @Value("${app.admin.list.max-page-size:50}") int maxPageSize) {
        this.adminBookingListRepository = adminBookingListRepository;
        this.maxPageSize = maxPageSize;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminBookingListItem> list(Pageable pageable, AdminBookingFilter filter) {
        Pageable effective = AdminListPageRequest.normalize(
                pageable, maxPageSize, SORTABLE, "createdAt", "id");
        Specification<Booking> specification = BookingSpecifications.from(filter);
        return PageResponse.from(adminBookingListRepository.findPage(effective, specification));
    }
}
