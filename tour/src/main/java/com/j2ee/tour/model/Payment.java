package com.j2ee.tour.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_booking", columnList = "booking_id"),
    @Index(name = "idx_payment_status", columnList = "payment_status"),
    @Index(name = "uq_payment_provider_txn", columnList = "payment_provider_txn_id", unique = true)
})
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @NotNull
    @DecimalMin("0.01")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paymentAmount;

    @NotNull
    @Pattern(regexp = "CASH|VNPAY|MOMO|CARD")
    @Column(nullable = false, length = 20)
    private String paymentMethod;

    @Column(length = 128)
    private String paymentProviderTxnId;

    @NotNull
    @Pattern(regexp = "PENDING|SUCCESS|FAILED")
    @Column(nullable = false, length = 20)
    private String paymentStatus;

    private LocalDateTime paymentPaidAt;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime paymentCreatedAt;

    @OneToMany(mappedBy = "payment")
    private List<Refund> refunds = new ArrayList<>();
}
