package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "accommodations")
public class Accommodation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accommodationId;

    private String accommodationName;
    private String location;
    private BigDecimal rating;          // DECIMAL(3,1)
    private BigDecimal pricePerNight;
    private String accommodationType;   // HOTEL / RESORT / HOMESTAY
}
