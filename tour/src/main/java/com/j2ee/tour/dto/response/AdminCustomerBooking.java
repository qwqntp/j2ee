package com.j2ee.tour.dto.response;

import java.math.BigDecimal;

public record AdminCustomerBooking(Long id, String code, String tourName, String status, BigDecimal total) {}
