package com.j2ee.tour.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Entity
@Table(name = "tour_guides")
public class TourGuide {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tourGuideId;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String tourGuideName;

    @Column(length = 255)
    private String tourGuideEmail;

    @Column(length = 20)
    private String tourGuidePhone;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer tourGuideExperienceYears;
}
