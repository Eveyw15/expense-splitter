package com.elec5619.group3.trip_planner_backend.controller;

import com.elec5619.group3.trip_planner_backend.dto.*;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.service.BalanceService;
import com.elec5619.group3.trip_planner_backend.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/trips/{tripId}")
public class SettlementController {
    private final BalanceService balances;
    private final SettlementService settlements;

    public SettlementController(BalanceService balances, SettlementService settlements) {
        this.balances = balances;
        this.settlements = settlements;
    }

    @GetMapping("/balances")
    public BalanceResponse balances(@PathVariable Long tripId, @AuthenticationPrincipal AuthenticatedUser user) {
        return balances.get(tripId, user.getUserId());
    }
    @GetMapping("/balances/simplified")
    public SimplifiedDebtResponse simplified(@PathVariable Long tripId, @AuthenticationPrincipal AuthenticatedUser user) {
        return balances.simplify(tripId, user.getUserId());
    }
    @GetMapping("/settlements")
    public List<SettlementResponse> history(@PathVariable Long tripId, @AuthenticationPrincipal AuthenticatedUser user) {
        return settlements.list(tripId, user.getUserId());
    }
    @PostMapping("/settlements") @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse create(@PathVariable Long tripId, @Valid @RequestBody SettlementRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        return settlements.create(tripId, request, user.getUserId());
    }
}
