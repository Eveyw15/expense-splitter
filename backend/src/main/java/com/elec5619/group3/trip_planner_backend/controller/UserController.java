package com.elec5619.group3.trip_planner_backend.controller;

import com.elec5619.group3.trip_planner_backend.dto.AuthResponse;
import com.elec5619.group3.trip_planner_backend.dto.ChangeEmailRequest;
import com.elec5619.group3.trip_planner_backend.dto.ChangePasswordRequest;
import com.elec5619.group3.trip_planner_backend.dto.UpdateProfileRequest;
import com.elec5619.group3.trip_planner_backend.dto.UserProfileResponse;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Deliberately NOT under /api/auth/** (which is permitAll) — this path
// falls under the default anyRequest().authenticated() rule, so hitting
// it is itself a live test of "is this token still valid".
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        AuthenticatedUser principal = (AuthenticatedUser)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        UserProfileResponse profile = authService.getCurrentUserProfile(principal.getUserId());
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateCurrentUser(@Valid @RequestBody UpdateProfileRequest request) {
        AuthenticatedUser principal = (AuthenticatedUser)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        UserProfileResponse profile = authService.updateProfile(principal.getUserId(), request);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        AuthenticatedUser principal = (AuthenticatedUser)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        authService.changePassword(principal.getUserId(), request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/me/email")
    public ResponseEntity<AuthResponse> changeEmail(@Valid @RequestBody ChangeEmailRequest request) {
        AuthenticatedUser principal = (AuthenticatedUser)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        AuthResponse response = authService.changeEmail(principal.getUserId(), request);
        return ResponseEntity.ok(response);
    }
}
