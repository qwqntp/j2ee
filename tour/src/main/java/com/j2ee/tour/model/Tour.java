package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "tours")
public class Tour {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tourId;

    private String tourName;
    private String tourDescription;
    private BigDecimal tourPrice;        // sau migration: doc gia tu TourDeparture
    private String tourStatus;           // DRAFT / OPEN / CLOSED / CANCELLED
    private LocalDateTime tourStartDate;
    private LocalDateTime tourEndDate;
    private String tourMeetingPoint;
    private Integer tourRemainingSlots;

    @ManyToOne
    @JoinColumn(name = "accommodation_id")
    private Accommodation accommodation;

    @ManyToOne
    @JoinColumn(name = "tour_guide_id")
    private TourGuide tourGuide;

    @OneToMany(mappedBy = "tour")
    private List<TourImage> tourImages = new ArrayList<>();

    @OneToMany(mappedBy = "tour")
    private List<TourDeparture> departures = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "tour_destinations",
            joinColumns = @JoinColumn(name = "tour_id"),
            inverseJoinColumns = @JoinColumn(name = "destination_id"))
    private List<TouristDestination> destinations = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "tour_vehicles",
            joinColumns = @JoinColumn(name = "tour_id"),
            inverseJoinColumns = @JoinColumn(name = "vehicle_id"))
    private List<TravelVehicle> vehicles = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "tour_promotions",
            joinColumns = @JoinColumn(name = "tour_id"),
            inverseJoinColumns = @JoinColumn(name = "promotion_id"))
    private List<Promotion> promotions = new ArrayList<>();
}
