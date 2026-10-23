package com.elec5619.group3.trip_planner_backend.repository;

import com.elec5619.group3.trip_planner_backend.entity.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripMemberRepository extends JpaRepository<TripMember, Long> {
    //FR:3
    // find trips specific user apart of
    @EntityGraph(attributePaths = {"trip"})
    List<TripMember> findByUser_UserId(Long userId);

    // find all the members in a trip
    @EntityGraph(attributePaths = {"user"})
    List<TripMember> findByTrip_TripId(Long tripId);

    // check if a user is in a trip
    Optional<TripMember> findByTrip_TripIdAndUser_UserId(Long tripId, Long userId);

    // a boolean check is user in this trip?
    boolean existsByTrip_TripIdAndUser_UserId(Long tripId, Long userId);


    //FR:4
    // count active members with specific role (ensures at least 1 joined owner remains)
    long countByTrip_TripIdAndRoleAndInviteStatus(Long tripId, String role, String inviteStatus);

    // find pending invitations for a specific user to display on their dashboard
    @EntityGraph(attributePaths = {"trip"})
    List<TripMember> findByUser_UserIdAndInviteStatus(Long userId, String inviteStatus);
}