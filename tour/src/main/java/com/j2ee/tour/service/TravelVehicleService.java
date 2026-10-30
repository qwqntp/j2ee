package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.TravelVehicleRequest;
import com.j2ee.tour.dto.response.TravelVehicleResponse;
import com.j2ee.tour.model.TravelVehicle;
import com.j2ee.tour.repository.TravelVehicleRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class TravelVehicleService {
    private final TravelVehicleRepository vehicleRepository;

    public TravelVehicleService(TravelVehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TravelVehicleResponse> list(Pageable pageable) {
        return PageResponse.from(vehicleRepository.findAll(pageable), TravelVehicleResponse::from);
    }

    @Transactional(readOnly = true)
    public TravelVehicleResponse get(Long id) {
        return TravelVehicleResponse.from(find(id));
    }

    @Transactional
    public TravelVehicleResponse create(TravelVehicleRequest request) {
        TravelVehicle vehicle = new TravelVehicle();
        apply(vehicle, request);
        return TravelVehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public TravelVehicleResponse update(Long id, TravelVehicleRequest request) {
        TravelVehicle vehicle = find(id);
        apply(vehicle, request);
        return TravelVehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void delete(Long id) {
        vehicleRepository.delete(find(id));
    }

    private void apply(TravelVehicle vehicle, TravelVehicleRequest request) {
        vehicle.setVehicleType(request.type());
        vehicle.setCapacity(request.capacity());
        vehicle.setRentalPricePerDay(request.rentalPricePerDay());
    }

    private TravelVehicle find(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Travel vehicle not found"));
    }
}
