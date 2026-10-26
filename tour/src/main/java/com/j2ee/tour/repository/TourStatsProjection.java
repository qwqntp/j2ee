package com.j2ee.tour.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface TourStatsProjection {

    Long getTourId();

    BigDecimal getPriceFrom();

    LocalDateTime getNextDepartureDate();

    Long getSeatsAvailable();

    Long getDepartureCount();
}
