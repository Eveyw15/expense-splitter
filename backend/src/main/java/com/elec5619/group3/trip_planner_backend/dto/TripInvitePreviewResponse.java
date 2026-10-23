package com.elec5619.group3.trip_planner_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * FR-4: What someone sees before joining a trip with an invite code.
 */
@Getter
@AllArgsConstructor
@Builder
public class TripInvitePreviewResponse {

    private Long tripId;
    private String name;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private long memberCount;
    private boolean alreadyMember;
}
