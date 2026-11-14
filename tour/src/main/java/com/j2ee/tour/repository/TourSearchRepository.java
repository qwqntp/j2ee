package com.j2ee.tour.repository;

import com.j2ee.tour.dto.request.TourSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * Database-side tour catalogue filtering and sorting. Kept as a fragment so the price/date
 * sort can order by an aggregate over upcoming departures, which Spring Data {@code Sort}
 * cannot express.
 */
public interface TourSearchRepository {

    Page<Long> searchTourIds(TourSearchCriteria criteria, LocalDateTime now, Pageable pageable);
}
