package com.elec5619.group3.trip_planner_backend.controller;

import com.elec5619.group3.trip_planner_backend.dto.RespondInviteRequest;
import com.elec5619.group3.trip_planner_backend.dto.TripMemberResponse;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.service.TripMemberService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips/invitations")
public class TripInvitationController {

    private final TripMemberService tripMemberService;

    public TripInvitationController(TripMemberService tripMemberService) {
        this.tripMemberService = tripMemberService;
    }

    /**
     * FR-4: Get the current user's pending trip invitations.
     * URL: GET http://localhost:8080/api/trips/invitations
     */
    @GetMapping
    public ResponseEntity<List<TripMemberResponse>> getMyInvitations() {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(tripMemberService.getMyPendingInvitations(currentUserId));
    }

    /**
     * FR-4: Accept or reject a trip invitation.
     * URL: POST http://localhost:8080/api/trips/invitations/{memberId}/respond
     * Returns the new membership on ACCEPT, or 204 No Content on REJECT.
     */
    @PostMapping("/{memberId}/respond")
    public ResponseEntity<TripMemberResponse> respondToInvite(
            @PathVariable Long memberId,
            @Valid @RequestBody RespondInviteRequest request) {
        Long currentUserId = getCurrentUserId();
        TripMemberResponse response = tripMemberService.respondToInvite(memberId, request, currentUserId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    private Long getCurrentUserId() {
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getUserId();
    }
}
