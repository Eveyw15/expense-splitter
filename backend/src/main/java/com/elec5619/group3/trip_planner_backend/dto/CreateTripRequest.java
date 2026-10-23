package com.elec5619.group3.trip_planner_backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CreateTripRequest {

    @NotBlank(message = "Trip name is required")
    @Size(max = 100, message = "Trip name must be at most 100 characters")
    private String name;

    @NotBlank(message = "Destination is required")
    @Size(max = 100, message = "Destination must be at most 100 characters")
    private String destination;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotNull(message = "Planned budget is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Planned budget cannot be negative")
    // Matches the planned_budget column (10 digits, 2 decimals).
    @Digits(integer = 8, fraction = 2, message = "Planned budget must be at most 99,999,999.99 with up to 2 decimal places")
    private BigDecimal plannedBudget;
    private String baseCurrency;
}