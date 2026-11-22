package com.j2ee.tour.dto.response;

import com.j2ee.tour.integration.VnpayGateway.Outcome;

/**
 * Payment state after an owned attempt was checked. {@code reason} is a safe diagnostic
 * code explaining why a {@code PENDING} attempt was not released; the two provider fields
 * carry documented two-digit VNPay codes only when VNPay supplied them.
 */
public record VnpayStatusResponse(
        Long bookingId, String reference, String status, boolean expired, Outcome returnOutcome,
        String reason, String providerCode, String providerStatus
) { }
