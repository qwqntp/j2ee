package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record InvoiceRequest(
        @NotNull Long bookingId,
        @NotBlank @Pattern(regexp = "PAID|UNPAID") String status,
        @NotNull @DecimalMin("0") BigDecimal discountAmount,
        @NotNull @DecimalMin("0") BigDecimal taxAmount,
        @NotNull @DecimalMin("0") BigDecimal serviceFee,
        @NotNull @DecimalMin("0") BigDecimal totalAmount,
        @Pattern(regexp = "CASH|VNPAY|MOMO|CARD") String paymentMethod,
        @Size(max = 255) String customerName,
        @Size(max = 20) String customerPhone,
        @Email @Size(max = 255) String customerEmail,
        List<Long> promotionIds
) {
}
