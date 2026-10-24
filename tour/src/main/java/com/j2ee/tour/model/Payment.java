package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tien that. Mot booking co the co nhieu lan payment (fail + retry) - giu lai de audit.
 */
@Data
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    private BigDecimal paymentAmount;
    private String paymentMethod;           // CASH / VNPAY / MOMO / CARD
    private String paymentProviderTxnId;    // ma giao dich gateway; NULL voi CASH
    private String paymentStatus;           // PENDING / SUCCESS / FAILED
    private LocalDateTime paymentPaidAt;
    private LocalDateTime paymentCreatedAt;

    @OneToMany(mappedBy = "payment")
    private List<Refund> refunds = new ArrayList<>();
}
