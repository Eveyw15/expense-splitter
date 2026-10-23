package com.elec5619.group3.trip_planner_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeEmailRequest {

    @NotBlank(message = "New email is required")
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be at most 100 characters")
    private String newEmail;

    public void setNewEmail(String newEmail) {
        this.newEmail = newEmail == null ? null : newEmail.trim();
    }
}
