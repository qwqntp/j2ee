package com.j2ee.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPaymentItem(Long id, Long bookingId, String bookingCode, LocalDateTime createdAt,
                               BigDecimal amount, String method, String status, String providerTransactionId) {}
