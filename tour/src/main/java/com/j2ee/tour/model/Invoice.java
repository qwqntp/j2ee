package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ban in cua don cu (du lieu cu). Doc lai bang bookings + payments cho logic moi.
 */
@Data
@Entity
@Table(name = "invoices")
public class Invoice {
    @Id
    private String invoiceId;

    private LocalDateTime invoiceCreatedAt;
    private String status;               // PAID / UNPAID
    private Integer numberOfPeople;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal serviceFee;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String customerName;         // snapshot
    private String customerPhone;
    private String customerEmail;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne
    @JoinColumn(name = "tour_id")
    private Tour tour;

    @ManyToMany
    @JoinTable(name = "invoice_promotions",
            joinColumns = @JoinColumn(name = "invoice_id"),
            inverseJoinColumns = @JoinColumn(name = "promotion_id"))
    private List<Promotion> promotions = new ArrayList<>();
}
