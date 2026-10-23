package com.elec5619.group3.trip_planner_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class TripMemberResponse {

    private Long tripMemberId;
    private Long tripId;
    private String tripName;
    private String tripDestination;
    private Long userId;
    private String username;
    private String email;
    private String role;
    private String inviteStatus;
    private LocalDateTime joinedAt;
}