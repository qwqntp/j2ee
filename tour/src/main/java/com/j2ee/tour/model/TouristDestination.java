package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "tourist_destinations")
public class TouristDestination {
    @Id
    private String destinationId;

    private String destinationName;
    private String location;
    private String description;
    private BigDecimal entryFee;
}
