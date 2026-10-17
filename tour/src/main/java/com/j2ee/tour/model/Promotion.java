package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "promotions")
public class Promotion {
    @Id
    private String promotionId;

    private String promotionName;
    private BigDecimal discountPercentage;
    private LocalDate startDate;
    private LocalDate endDate;
    private String description;
}
