package com.elec5619.group3.trip_planner_backend.controller;

import com.elec5619.group3.trip_planner_backend.dto.TripInvitePreviewResponse;
import com.elec5619.group3.trip_planner_backend.dto.TripMemberResponse;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.service.TripMemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/join")
public class TripJoinController {

    private final TripMemberService tripMemberService;

    public TripJoinController(TripMemberService tripMemberService) {
        this.tripMemberService = tripMemberService;
    }

    /**
     * FR-4: Preview a trip from its invite code before joining.
     * URL: GET http://localhost:8080/api/trips/join/{inviteCode}
     */
    @GetMapping("/{inviteCode}")
    public ResponseEntity<TripInvitePreviewResponse> previewTrip(@PathVariable String inviteCode) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(tripMemberService.previewTripByInviteCode(inviteCode, currentUserId));
    }

    /**
     * FR-4: Join a trip using its invite code.
     * URL: POST http://localhost:8080/api/trips/join/{inviteCode}
     */
    @PostMapping("/{inviteCode}")
    public ResponseEntity<TripMemberResponse> joinTrip(@PathVariable String inviteCode) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(tripMemberService.joinTripByInviteCode(inviteCode, currentUserId));
    }

    private Long getCurrentUserId() {
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getUserId();
    }
}
