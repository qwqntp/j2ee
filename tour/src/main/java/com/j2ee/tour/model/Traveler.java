package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

/**
 * Khach di thuc te cua mot booking (guest manifest).
 * Khong phan loai tre em / nguoi lon.
 */
@Data
@Entity
@Table(name = "travelers")
public class Traveler {
    @Id
    private String travelerId;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    private String travelerName;
    private LocalDate travelerDateOfBirth;
    private String travelerGender;     // MALE / FEMALE / OTHER
    private String travelerIdDocument; // so CCCD / ho chieu
}
