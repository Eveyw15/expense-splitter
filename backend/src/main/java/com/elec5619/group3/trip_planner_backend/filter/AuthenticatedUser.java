package com.elec5619.group3.trip_planner_backend.filter;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The principal stored in SecurityContextHolder for a JWT-authenticated request.
 * Lets Controllers/Services read the current user's id without an extra
 * repository lookup by email.
 */
@Getter
@AllArgsConstructor
public class AuthenticatedUser {

    private final Long userId;
    private final String email;
}
