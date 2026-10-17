package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "tour_guides")
public class TourGuide {
    @Id
    private String tourGuideId;

    private String tourGuideName;
    private String tourGuideEmail;
    private String tourGuidePhone;
    private Integer tourGuideExperienceYears;
}
