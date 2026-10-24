package com.j2ee.tour.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @NotBlank
    @Column(unique = true, nullable = false, length = 16)
    private String bookingCode;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime bookingCreatedAt;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer bookingGuestCount;

    @NotNull
    @DecimalMin("0")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal bookingUnitPrice;

    @NotNull
    @DecimalMin("0")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal bookingSubtotal;

    @NotNull
    @DecimalMin("0")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal bookingDiscountAmount;

    @NotNull
    @DecimalMin("0")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal bookingTotalAmount;

    @NotNull
    @Pattern(regexp = "PENDING|PAID|CONFIRMED|COMPLETED|CANCELLED")
    @Column(nullable = false, length = 20)
    private String bookingStatus;

    @Column(columnDefinition = "TEXT")
    private String bookingNote;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "departure_id", nullable = false)
    private TourDeparture departure;

    @OneToMany(mappedBy = "booking")
    private List<Traveler> travelers = new ArrayList<>();

    @OneToMany(mappedBy = "booking")
    private List<Payment> payments = new ArrayList<>();
}
