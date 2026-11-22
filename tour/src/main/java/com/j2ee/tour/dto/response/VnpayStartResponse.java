package com.j2ee.tour.dto.response;

import java.net.URI;
import java.time.Instant;

public record VnpayStartResponse(String reference, URI paymentUrl, Instant expiresAt) { }
