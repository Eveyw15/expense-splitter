package com.elec5619.group3.trip_planner_backend.service;

import com.elec5619.group3.trip_planner_backend.dto.AuthResponse;
import com.elec5619.group3.trip_planner_backend.dto.ChangeEmailRequest;
import com.elec5619.group3.trip_planner_backend.dto.ChangePasswordRequest;
import com.elec5619.group3.trip_planner_backend.dto.LoginRequest;
import com.elec5619.group3.trip_planner_backend.dto.RegisterRequest;
import com.elec5619.group3.trip_planner_backend.dto.UpdateProfileRequest;
import com.elec5619.group3.trip_planner_backend.dto.UserProfileResponse;
import com.elec5619.group3.trip_planner_backend.entity.User;
import com.elec5619.group3.trip_planner_backend.repository.UserRepository;
import com.elec5619.group3.trip_planner_backend.util.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);

        String token = jwtUtil.generateToken(
                savedUser.getUserId(), savedUser.getEmail(), savedUser.getRole());

        return new AuthResponse(token, savedUser.getUserId(),
                savedUser.getUsername(), savedUser.getEmail(), savedUser.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(
                user.getUserId(), user.getEmail(), user.getRole());

        return new AuthResponse(token, user.getUserId(),
                user.getUsername(), user.getEmail(), user.getRole());
    }

    public UserProfileResponse getCurrentUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        return new UserProfileResponse(
                user.getUserId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        user.setUsername(request.getUsername());
        User savedUser = userRepository.save(user);

        return new UserProfileResponse(
                savedUser.getUserId(), savedUser.getUsername(), savedUser.getEmail(), savedUser.getRole());
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public AuthResponse changeEmail(Long userId, ChangeEmailRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        boolean emailChanged = !request.getNewEmail().equalsIgnoreCase(user.getEmail());
        if (emailChanged && userRepository.existsByEmail(request.getNewEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        user.setEmail(request.getNewEmail());
        User savedUser = userRepository.save(user);

        // Email is the JWT subject, so a fresh token is issued in the same
        // response — no forced logout, unlike changePassword().
        String token = jwtUtil.generateToken(
                savedUser.getUserId(), savedUser.getEmail(), savedUser.getRole());

        return new AuthResponse(token, savedUser.getUserId(),
                savedUser.getUsername(), savedUser.getEmail(), savedUser.getRole());
    }
}
