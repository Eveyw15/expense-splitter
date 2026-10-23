package com.elec5619.group3.trip_planner_backend.repository;

import com.elec5619.group3.trip_planner_backend.entity.PersonalBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PersonalBudgetRepository extends JpaRepository<PersonalBudget, Long> {
    Optional<PersonalBudget> findByTrip_TripIdAndUser_UserId(Long tripId, Long userId);
    void deleteByTrip_TripId(Long tripId);
}
