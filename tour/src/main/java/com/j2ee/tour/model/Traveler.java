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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "travelers", indexes = {
    @Index(name = "idx_traveler_booking", columnList = "booking_id")
})
public class Traveler {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long travelerId;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String travelerName;

    private LocalDate travelerDateOfBirth;

    @Pattern(regexp = "MALE|FEMALE|OTHER")
    @Column(length = 10)
    private String travelerGender;

    @Column(length = 64)
    private String travelerIdDocument;
}
