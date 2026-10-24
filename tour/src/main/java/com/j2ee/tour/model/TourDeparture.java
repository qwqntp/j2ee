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
 * Kon t? (lich khoi han) cua mot Tour: ngay di, so cho, gia.
 * Booking tro vao bang nay, KHONG tro vao Tour.
 */
@Data
@Entity
@Table(name = "tour_departures")
public class TourDeparture {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long departureId;

    @ManyToOne
    @JoinColumn(name = "tour_id")
    private Tour tour;

    private LocalDateTime departureStartDate;
    private LocalDateTime departureEndDate;
    private Integer departureCapacity;
    private Integer departureBookedSlots;
    private BigDecimal departurePrice;
    private String departureMeetingPoint;
    private String departureStatus;       // OPEN / FULL / CLOSED / CANCELLED
    private LocalDateTime departureCreatedAt;

    public Integer getSeatsAvailable() {
        if (departureCapacity == null) return 0;
        return departureCapacity - (departureBookedSlots == null ? 0 : departureBookedSlots);
    }
}
