package com.elec5619.group3.trip_planner_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class TripResponse {

    private Long tripId;
    private String name;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal plannedBudget;
    private String baseCurrency;
    private String status;
    private String role;
    private LocalDateTime createdAt;
    // Served at GET /api/images/{coverImageId}; null when no cover photo is set.
    private String coverImageId;
}