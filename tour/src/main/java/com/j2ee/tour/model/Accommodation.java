package com.j2ee.tour.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "accommodations")
public class Accommodation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accommodationId;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String accommodationName;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String location;

    @DecimalMin("0")
    @DecimalMax("5")
    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    @DecimalMin("0")
    @Column(precision = 12, scale = 2)
    private BigDecimal pricePerNight;

    @Pattern(regexp = "HOTEL|RESORT|HOMESTAY")
    @Column(length = 20)
    private String accommodationType;
}
