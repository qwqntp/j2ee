package com.j2ee.tour.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tour_departures", indexes = {
    @Index(name = "idx_departure_tour", columnList = "tour_id"),
    @Index(name = "idx_departure_status_date", columnList = "departure_status, departure_start_date")
})
public class TourDeparture {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long departureId;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "tour_id", nullable = false)
    private Tour tour;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime departureStartDate;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime departureEndDate;

    @NotNull
    @Min(1)
    @Column(nullable = false)
    private Integer departureCapacity;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer departureBookedSlots;

    @NotNull
    @DecimalMin("0")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal departurePrice;

    @Column(length = 255)
    private String departureMeetingPoint;

    @NotNull
    @Pattern(regexp = "OPEN|FULL|CLOSED|CANCELLED")
    @Column(nullable = false, length = 20)
    private String departureStatus;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime departureCreatedAt;

    public Integer getSeatsAvailable() {
        if (departureCapacity == null) return 0;
        return departureCapacity - (departureBookedSlots == null ? 0 : departureBookedSlots);
    }
}
