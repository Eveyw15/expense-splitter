package com.elec5619.group3.trip_planner_backend.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PersonalBudgetRequest(@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
                                    @NotBlank String currencyCode) {}
