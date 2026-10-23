package com.elec5619.group3.trip_planner_backend.repository;

import com.elec5619.group3.trip_planner_backend.entity.UpdateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UpdateHistoryRepository extends JpaRepository<UpdateHistory, Long> {
    List<UpdateHistory> findTop50ByTrip_TripIdOrderByChangedAtDescLogIdDesc(Long tripId);
    boolean existsByTrip_TripIdAndChangeTypeAndDescriptionStartingWith(
            Long tripId, String changeType, String descriptionPrefix);
}
