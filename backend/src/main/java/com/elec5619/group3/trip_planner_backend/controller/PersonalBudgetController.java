package com.elec5619.group3.trip_planner_backend.controller;

import com.elec5619.group3.trip_planner_backend.dto.*;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.service.PersonalBudgetService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}/personal-budget")
public class PersonalBudgetController {
    private final PersonalBudgetService service;
    public PersonalBudgetController(PersonalBudgetService service) { this.service = service; }
    @GetMapping
    public ResponseEntity<PersonalBudgetResponse> get(@PathVariable Long tripId, @AuthenticationPrincipal AuthenticatedUser user) {
        var budget = service.get(tripId, user.getUserId());
        return budget == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(budget);
    }
    @PutMapping
    public PersonalBudgetResponse save(@PathVariable Long tripId, @AuthenticationPrincipal AuthenticatedUser user,
                                       @Valid @RequestBody PersonalBudgetRequest request) {
        return service.save(tripId, user.getUserId(), request);
    }
}
