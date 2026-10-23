package com.elec5619.group3.trip_planner_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ExpenseSummaryResponse {
    private BigDecimal totalSpending;
    private BigDecimal budget;
    private BigDecimal remaining;
    private BigDecimal currentUserOwes;
    private BigDecimal budgetUsedPercentage;
}
