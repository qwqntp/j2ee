package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hoan tien - thuc hien tren mot payment cu the. Partial refund duoc phep.
 */
@Data
@Entity
@Table(name = "refunds")
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long refundId;

    @ManyToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;       // luu lat de truy van nhanh

    private BigDecimal refundAmount;
    private String refundReason;
    private String refundStatus;   // REQUESTED / APPROVED / REJECTED / DONE
    private LocalDateTime refundRequestedAt;
    private LocalDateTime refundProcessedAt;
}
