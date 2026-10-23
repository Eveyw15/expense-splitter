package com.elec5619.group3.trip_planner_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RespondInviteRequest {

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "^(ACCEPT|REJECT)$", message = "Action must be either ACCEPT or REJECT")
    private String action;
}