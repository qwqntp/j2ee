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
 * Don dat cho truoc: het ghe sinh vien dat (Customer mua; traveler di).
 * Booking chua PAID khi SUM(payment SUCCESS) >= totalAmount.
 */
@Data
@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    private String bookingCode;            // 'BK-YYYYMMDD-0001'
    private LocalDateTime bookingCreatedAt;
    private Integer bookingGuestCount;
    private BigDecimal bookingUnitPrice;   // snapshot gia tai thoi diem dat
    private BigDecimal bookingSubtotal;
    private BigDecimal bookingDiscountAmount;
    private BigDecimal bookingTotalAmount;
    private String bookingStatus;          // PENDING / PAID / CONFIRMED / COMPLETED / CANCELLED
    private String bookingNote;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne
    @JoinColumn(name = "departure_id")
    private TourDeparture departure;

    @OneToMany(mappedBy = "booking")
    private List<Traveler> travelers = new ArrayList<>();

    @OneToMany(mappedBy = "booking")
    private List<Payment> payments = new ArrayList<>();
}
