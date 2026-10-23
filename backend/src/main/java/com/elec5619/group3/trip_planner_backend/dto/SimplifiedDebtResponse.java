package com.elec5619.group3.trip_planner_backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record SimplifiedDebtResponse(String currency, long originalPairwiseRelationships, List<Transfer> transfers) {
    public record Transfer(Long fromUserId, String fromName, Long toUserId, String toName, BigDecimal amount) {}
}
