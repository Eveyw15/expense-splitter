package com.elec5619.group3.trip_planner_backend.controller;

import com.elec5619.group3.trip_planner_backend.dto.UpdateHistoryResponse;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.service.UpdateHistoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/trips/{tripId}/updates")
public class UpdateHistoryController {
    private final UpdateHistoryService service;

    public UpdateHistoryController(UpdateHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<UpdateHistoryResponse> list(@PathVariable Long tripId,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(tripId, user.getUserId());
    }
}
