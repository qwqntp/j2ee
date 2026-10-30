package com.j2ee.tour.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "tours", indexes = {
    @Index(name = "idx_tour_status", columnList = "tour_status")
})
public class Tour {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tourId;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String tourName;

    @Column(columnDefinition = "TEXT")
    private String tourDescription;

    @Column
    private Integer tourDurationDays;

    @NotNull
    @Pattern(regexp = "DRAFT|OPEN|CLOSED|CANCELLED")
    @Column(nullable = false, length = 20)
    private String tourStatus;

    @ManyToOne
    @JoinColumn(name = "accommodation_id")
    private Accommodation accommodation;

    @ManyToOne
    @JoinColumn(name = "tour_guide_id")
    private TourGuide tourGuide;

    @ManyToOne
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

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
}
