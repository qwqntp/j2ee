package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "travel_vehicles")
public class TravelVehicle {
    @Id
    private String vehicleId;

    private String vehicleType;
    private Integer capacity;
    private BigDecimal rentalPricePerDay;
}
