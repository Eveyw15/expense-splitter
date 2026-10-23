package com.elec5619.group3.trip_planner_backend.service;

import com.elec5619.group3.trip_planner_backend.dto.InviteCodeResponse;
import com.elec5619.group3.trip_planner_backend.dto.InviteMemberRequest;
import com.elec5619.group3.trip_planner_backend.dto.RespondInviteRequest;
import com.elec5619.group3.trip_planner_backend.dto.TripInvitePreviewResponse;
import com.elec5619.group3.trip_planner_backend.dto.TripMemberResponse;
import com.elec5619.group3.trip_planner_backend.dto.UpdateMemberRoleRequest;
import com.elec5619.group3.trip_planner_backend.entity.Trip;
import com.elec5619.group3.trip_planner_backend.entity.TripMember;
import com.elec5619.group3.trip_planner_backend.entity.User;
import com.elec5619.group3.trip_planner_backend.exception.ResourceNotFoundException;
import com.elec5619.group3.trip_planner_backend.repository.TripMemberRepository;
import com.elec5619.group3.trip_planner_backend.repository.TripRepository;
import com.elec5619.group3.trip_planner_backend.repository.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TripMemberService {

    // No 0/O or 1/I so codes are easy to read aloud and type.
    private static final String INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final SecureRandom INVITE_CODE_RANDOM = new SecureRandom();
    private static final String INVALID_INVITE_CODE =
            "Invite code not found. It may have been reset by the trip owner.";

    private final TripMemberRepository tripMemberRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    public TripMemberService(
            TripMemberRepository tripMemberRepository,
            TripRepository tripRepository,
            UserRepository userRepository) {

        this.tripMemberRepository = tripMemberRepository;
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
    }

    /**
     * FR-4: View all active members participating in the trip.
     * Allowed: Any active trip member.
     */
    @Transactional(readOnly = true)
    public List<TripMemberResponse> getTripMembers(
            Long tripId,
            Long currentUserId) {

        verifyUserIsMember(tripId, currentUserId);

        return tripMemberRepository
                .findByTrip_TripId(tripId)
                .stream()
                .filter(TripMember::isActive)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * FR-4: View invitations for this trip that have not been accepted yet.
     * Kept separate from getTripMembers so expense splits and activity
     * assignment never see users who haven't joined.
     * Allowed: Any active trip member.
     */
    @Transactional(readOnly = true)
    public List<TripMemberResponse> getPendingTripInvites(
            Long tripId,
            Long currentUserId) {

        verifyUserIsMember(tripId, currentUserId);

        return tripMemberRepository
                .findByTrip_TripId(tripId)
                .stream()
                .filter(member -> !member.isActive())
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * FR-4: Invite another registered user to join the trip.
     * Allowed: Any active trip member.
     */
    @Transactional
    public TripMemberResponse inviteMember(
            Long tripId,
            InviteMemberRequest request,
            Long currentUserId) {

        // Locked first (see lockTripForMembershipChange) so two simultaneous
        // invites of the same person can't both pass the duplicate check below.
        Trip trip = lockTripForMembershipChange(tripId);

        verifyUserIsMember(tripId, currentUserId);

        User userToInvite = userRepository
                .findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No registered user found with email: "
                                        + request.getEmail()
                        ));

        tripMemberRepository
                .findByTrip_TripIdAndUser_UserId(
                        tripId,
                        userToInvite.getUserId()
                )
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            existing.isActive()
                                    ? "User is already a member of this trip"
                                    : "User has already been invited to this trip"
                    );
                });

        TripMember newMember = new TripMember();
        newMember.setTrip(trip);
        newMember.setUser(userToInvite);
        newMember.setRole("MEMBER");

        newMember.setInviteStatus("PENDING");

        TripMember saved =
                tripMemberRepository.save(newMember);

        return mapToResponse(saved);
    }

    /**
     * FR-4 / FR-2:
     * Update a member's role.
     * Allowed: Trip owners only.
     *
     * At least one OWNER must remain.
     */
    @Transactional
    public TripMemberResponse updateMemberRole(
            Long tripId,
            Long targetMemberId,
            UpdateMemberRoleRequest request,
            Long currentUserId) {

        // Locked first so two owners demoting each other at the same moment
        // are handled one at a time and the owner count below is current.
        lockTripForMembershipChange(tripId);

        verifyUserIsOwner(tripId, currentUserId);

        TripMember targetMember =
                tripMemberRepository.findById(targetMemberId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Trip member not found"
                                ));

        if (!targetMember.getTrip()
                .getTripId()
                .equals(tripId)) {

            throw new IllegalArgumentException(
                    "Member does not belong to this trip"
            );
        }

        if (!targetMember.isActive()) {
            throw new IllegalStateException(
                    "Cannot change the role of a pending invitation"
            );
        }

        String newRole =
                request.getRole().toUpperCase();

        if ("OWNER".equals(targetMember.getRole())
                && "MEMBER".equals(newRole)) {

            long ownerCount = tripMemberRepository.countByTrip_TripIdAndRoleAndInviteStatus(tripId, "OWNER", "JOINED");

            if (ownerCount <= 1) {
                throw new IllegalStateException(
                        "Cannot demote the last remaining trip owner. "
                                + "Grant another member owner role first."
                );
            }
        }

        targetMember.setRole(newRole);

        TripMember updated =
                tripMemberRepository.save(targetMember);

        return mapToResponse(updated);
    }

    /**
     * FR-4:
     * Remove another member or leave the trip.
     *
     * Owner:
     * - may remove another member
     *
     * Member:
     * - may remove themselves
     *
     * Last OWNER cannot leave or be removed.
     */
    @Transactional
    public void removeMember(
            Long tripId,
            Long targetMemberId,
            Long currentUserId) {

        // Locked first so two owners leaving at the same moment are handled
        // one at a time and the owner count below is current.
        lockTripForMembershipChange(tripId);

        TripMember callerMembership =
                verifyUserIsMember(
                        tripId,
                        currentUserId
                );

        TripMember targetMember =
                tripMemberRepository.findById(targetMemberId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Trip member not found"
                                ));

        if (!targetMember.getTrip()
                .getTripId()
                .equals(tripId)) {

            throw new IllegalArgumentException(
                    "Member does not belong to this trip"
            );
        }

        boolean isSelf =
                targetMember.getUser()
                        .getUserId()
                        .equals(currentUserId);

        boolean isOwner =
                "OWNER".equals(callerMembership.getRole());

        if (!isOwner && !isSelf) {
            throw new AccessDeniedException(
                    "Only a trip owner can remove other members"
            );
        }

        if ("OWNER".equals(targetMember.getRole()) && targetMember.isActive()) {
            long ownerCount = tripMemberRepository.countByTrip_TripIdAndRoleAndInviteStatus(tripId, "OWNER", "JOINED");

            if (ownerCount <= 1) {
                throw new IllegalStateException(
                        "Cannot remove or leave as the last remaining "
                                + "trip owner. Transfer ownership first."
                );
            }
        }

        tripMemberRepository.delete(targetMember);
    }

    /**
     * Shared membership guard.
     *
     * Used by Trip functionality.
     */
    @Transactional(readOnly = true)
    public TripMember verifyUserIsMember(
            Long tripId,
            Long userId) {

        if (!tripRepository.existsById(tripId)) {
            throw new ResourceNotFoundException(
                    "Trip not found"
            );
        }

        return tripMemberRepository
                .findByTrip_TripIdAndUser_UserId(
                        tripId,
                        userId
                )
                .filter(TripMember::isActive)
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "You are not a member of this trip"
                        ));
    }

    /**
     * FR-4: Get all pending trip invitations for the logged-in user.
     */
    @Transactional(readOnly = true)
    public List<TripMemberResponse> getMyPendingInvitations(Long currentUserId) {
        return tripMemberRepository
                .findByUser_UserIdAndInviteStatus(currentUserId, "PENDING")
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * FR-4: Accept or reject a trip invitation.
     */
    @Transactional
    public TripMemberResponse respondToInvite(
            Long tripMemberId,
            RespondInviteRequest request,
            Long currentUserId) {

        TripMember invitation = tripMemberRepository.findById(tripMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        if (!invitation.getUser().getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("You cannot respond to another user's invitation");
        }

        if (!"PENDING".equals(invitation.getInviteStatus())) {
            throw new IllegalStateException("Invitation has already been responded to");
        }

        if ("ACCEPT".equalsIgnoreCase(request.getAction())) {
            invitation.setInviteStatus("JOINED");
            // joinedAt was stamped at invite time; record when they actually joined.
            invitation.setJoinedAt(LocalDateTime.now());
            TripMember updated = tripMemberRepository.save(invitation);
            return mapToResponse(updated);
        } else {
            tripMemberRepository.delete(invitation);
            return null;
        }
    }

    /**
     * FR-4: Get the trip's shareable invite code, creating one on first use.
     * Allowed: Any active trip member (members may invite others).
     */
    @Transactional
    public InviteCodeResponse getInviteCode(
            Long tripId,
            Long currentUserId) {

        verifyUserIsMember(tripId, currentUserId);

        Trip trip = lockTripForMembershipChange(tripId);

        if (trip.getInviteCode() == null) {
            trip.setInviteCode(generateUniqueInviteCode());
            tripRepository.save(trip);
        }

        return new InviteCodeResponse(trip.getInviteCode());
    }

    /**
     * FR-4: Replace the invite code so the old code and QR stop working.
     * Allowed: Trip owners only.
     */
    @Transactional
    public InviteCodeResponse regenerateInviteCode(
            Long tripId,
            Long currentUserId) {

        verifyUserIsOwner(tripId, currentUserId);

        Trip trip = lockTripForMembershipChange(tripId);
        trip.setInviteCode(generateUniqueInviteCode());
        tripRepository.save(trip);

        return new InviteCodeResponse(trip.getInviteCode());
    }

    /**
     * FR-4: Show basic trip details for an invite code before joining.
     * Allowed: Any logged-in user holding the code.
     */
    @Transactional(readOnly = true)
    public TripInvitePreviewResponse previewTripByInviteCode(
            String inviteCode,
            Long currentUserId) {

        Trip trip = tripRepository
                .findByInviteCode(normalizeInviteCode(inviteCode))
                .orElseThrow(() ->
                        new ResourceNotFoundException(INVALID_INVITE_CODE));

        List<TripMember> activeMembers = tripMemberRepository
                .findByTrip_TripId(trip.getTripId())
                .stream()
                .filter(TripMember::isActive)
                .toList();

        return TripInvitePreviewResponse.builder()
                .tripId(trip.getTripId())
                .name(trip.getName())
                .destination(trip.getDestination())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .status(trip.getStatus())
                .memberCount(activeMembers.size())
                .alreadyMember(activeMembers.stream()
                        .anyMatch(member -> member.getUser().getUserId().equals(currentUserId)))
                .build();
    }

    /**
     * FR-4: Join a trip with its invite code. Joins immediately as MEMBER.
     * Joining when already a member is a no-op; a pending email invite
     * for the same trip is treated as accepted.
     */
    @Transactional
    public TripMemberResponse joinTripByInviteCode(
            String inviteCode,
            Long currentUserId) {

        Trip trip = tripRepository
                .lockByInviteCode(normalizeInviteCode(inviteCode))
                .orElseThrow(() ->
                        new ResourceNotFoundException(INVALID_INVITE_CODE));

        Optional<TripMember> existing = tripMemberRepository
                .findByTrip_TripIdAndUser_UserId(trip.getTripId(), currentUserId);

        if (existing.isPresent() && existing.get().isActive()) {
            return mapToResponse(existing.get());
        }

        if ("COMPLETED".equals(trip.getStatus())) {
            throw new IllegalStateException(
                    "This trip has been completed and can no longer be joined"
            );
        }

        TripMember membership;

        if (existing.isPresent()) {
            membership = existing.get();
            membership.setInviteStatus("JOINED");
            membership.setJoinedAt(LocalDateTime.now());
        } else {
            User user = userRepository.findById(currentUserId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("User not found"));

            membership = new TripMember();
            membership.setTrip(trip);
            membership.setUser(user);
            membership.setRole("MEMBER");
            membership.setInviteStatus("JOINED");
        }

        return mapToResponse(tripMemberRepository.save(membership));
    }

    /**
     * Takes a row lock on the trip so membership changes for one trip run one
     * at a time. Call it before any other query in the transaction: a check
     * made before the lock could be based on data that is already out of date.
     */
    private Trip lockTripForMembershipChange(Long tripId) {
        return tripRepository.lockForMembershipChange(tripId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Trip not found"));
    }

    // Accept codes typed with spaces, dashes or lowercase.
    private String normalizeInviteCode(String inviteCode) {
        return inviteCode == null
                ? ""
                : inviteCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                builder.append(INVITE_CODE_ALPHABET.charAt(
                        INVITE_CODE_RANDOM.nextInt(INVITE_CODE_ALPHABET.length())));
            }
            code = builder.toString();
        } while (tripRepository.existsByInviteCode(code));
        return code;
    }


    /**
     * Shared owner authorization guard.
     */
    @Transactional(readOnly = true)
    public TripMember verifyUserIsOwner(
            Long tripId,
            Long userId) {

        TripMember member =
                verifyUserIsMember(
                        tripId,
                        userId
                );

        if (!"OWNER".equals(member.getRole())) {
            throw new AccessDeniedException(
                    "Only trip owners can perform this action"
            );
        }

        return member;
    }

    private TripMemberResponse mapToResponse(TripMember member) {
        return TripMemberResponse.builder()
                .tripMemberId(member.getTripMemberId())
                .tripId(member.getTrip().getTripId())
                .tripName(member.getTrip().getName())
                .tripDestination(member.getTrip().getDestination())
                .userId(member.getUser().getUserId())
                .username(member.getUser().getUsername())
                .email(member.getUser().getEmail())
                .role(member.getRole())
                .inviteStatus(member.getInviteStatus())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}