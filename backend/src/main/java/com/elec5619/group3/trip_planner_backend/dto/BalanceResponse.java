package com.elec5619.group3.trip_planner_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;

@Getter
@AllArgsConstructor
public class BalanceResponse {
    private BigDecimal netBalance;
    private BigDecimal totalYouOwe;
    private BigDecimal totalYouAreOwed;
    private List<Obligation> youOwe;
    private List<Obligation> youAreOwed;

    @Getter
    @AllArgsConstructor
    public static class Obligation {
        private Long userId;
        private String name;
        private BigDecimal amount;
    }
}
