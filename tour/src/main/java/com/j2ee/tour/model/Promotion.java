package com.j2ee.tour.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "promotions")
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promotionId;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String promotionName;

    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @NotNull
    @Column(nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(nullable = false)
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 64)
    private String promotionCode;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean promotionActive = true;

    @Min(0)
    @Column(name = "usage_limit")
    private Integer promotionUsageLimit;

    @NotNull
    @Min(0)
    @Column(name = "used_count", nullable = false)
    private Integer promotionUsedCount = 0;

    @DecimalMin("0")
    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal promotionMinOrderAmount;
}
