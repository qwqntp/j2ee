package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.Pattern;

public record RefundUpdateRequest(
        @Pattern(regexp = "REQUESTED|APPROVED|REJECTED|DONE") String status,
        String reason
) {
}
