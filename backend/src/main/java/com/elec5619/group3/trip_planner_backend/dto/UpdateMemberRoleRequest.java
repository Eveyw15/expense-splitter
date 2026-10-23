package com.elec5619.group3.trip_planner_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMemberRoleRequest {

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(OWNER|MEMBER)$", message = "Role must be either OWNER or MEMBER")
    private String role;
}