package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.TravelVehicle;

import java.math.BigDecimal;

public record TravelVehicleResponse(
        Long id,
        String type,
        Integer capacity,
        BigDecimal rentalPricePerDay
) {
    public static TravelVehicleResponse from(TravelVehicle vehicle) {
        return new TravelVehicleResponse(
                vehicle.getVehicleId(),
                vehicle.getVehicleType(),
                vehicle.getCapacity(),
                vehicle.getRentalPricePerDay()
        );
    }
}
