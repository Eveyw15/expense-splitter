package com.elec5619.group3.trip_planner_backend.controller;

import com.elec5619.group3.trip_planner_backend.dto.InviteCodeResponse;
import com.elec5619.group3.trip_planner_backend.dto.InviteMemberRequest;
import com.elec5619.group3.trip_planner_backend.dto.TripMemberResponse;
import com.elec5619.group3.trip_planner_backend.dto.UpdateMemberRoleRequest;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.service.TripMemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips/{tripId}/members")
public class TripMemberController {

    private final TripMemberService tripMemberService;

    public TripMemberController(TripMemberService tripMemberService) {
        this.tripMemberService = tripMemberService;
    }

    /**
     * FR-4: Get all members for a trip.
     * URL: GET http://localhost:8080/api/trips/{tripId}/members
     */
    @GetMapping
    public ResponseEntity<List<TripMemberResponse>> getTripMembers(@PathVariable Long tripId) {
        Long currentUserId = getCurrentUserId();
        List<TripMemberResponse> members = tripMemberService.getTripMembers(tripId, currentUserId);
        return ResponseEntity.ok(members);
    }

    /**
     * FR-4: Get invitations for this trip that are still awaiting a response.
     * URL: GET http://localhost:8080/api/trips/{tripId}/members/pending
     */
    @GetMapping("/pending")
    public ResponseEntity<List<TripMemberResponse>> getPendingTripInvites(@PathVariable Long tripId) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(tripMemberService.getPendingTripInvites(tripId, currentUserId));
    }

    /**
     * FR-4: Get the trip's shareable invite code (for the link/QR).
     * URL: GET http://localhost:8080/api/trips/{tripId}/members/invite-code
     */
    @GetMapping("/invite-code")
    public ResponseEntity<InviteCodeResponse> getInviteCode(@PathVariable Long tripId) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(tripMemberService.getInviteCode(tripId, currentUserId));
    }

    /**
     * FR-4: Reset the invite code so the old code/QR stops working (Owner only).
     * URL: POST http://localhost:8080/api/trips/{tripId}/members/invite-code/regenerate
     */
    @PostMapping("/invite-code/regenerate")
    public ResponseEntity<InviteCodeResponse> regenerateInviteCode(@PathVariable Long tripId) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(tripMemberService.regenerateInviteCode(tripId, currentUserId));
    }

    /**
     * FR-4: Invite another user to join the trip.
     * URL: POST http://localhost:8080/api/trips/{tripId}/members/invite
     */
    @PostMapping("/invite")
    public ResponseEntity<TripMemberResponse> inviteMember(
            @PathVariable Long tripId,
            @Valid @RequestBody InviteMemberRequest request) {
        Long currentUserId = getCurrentUserId();
        TripMemberResponse response = tripMemberService.inviteMember(tripId, request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * FR-4 / FR-2: Update a member's role (OWNER <-> MEMBER).
     * URL: PUT http://localhost:8080/api/trips/{tripId}/members/{memberId}/role
     */
    @PutMapping("/{memberId}/role")
    public ResponseEntity<TripMemberResponse> updateMemberRole(
            @PathVariable Long tripId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        Long currentUserId = getCurrentUserId();
        TripMemberResponse response = tripMemberService.updateMemberRole(tripId, memberId, request, currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * FR-4: Remove a member or leave the trip.
     * URL: DELETE http://localhost:8080/api/trips/{tripId}/members/{memberId}
     */
    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long tripId,
            @PathVariable Long memberId) {
        Long currentUserId = getCurrentUserId();
        tripMemberService.removeMember(tripId, memberId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Helper to read the current authenticated user's ID from the JWT token.
     */
    private Long getCurrentUserId() {
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal.getUserId();
    }
}