package com.elec5619.group3.trip_planner_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PersonalBudgetResponse(BigDecimal amount, String currencyCode, BigDecimal exchangeRateToBase,
                                     BigDecimal baseAmount, LocalDate exchangeRateDate, LocalDateTime updatedAt) {}
