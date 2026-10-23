package com.elec5619.group3.trip_planner_backend.repository;

import com.elec5619.group3.trip_planner_backend.entity.Settlement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    @EntityGraph(attributePaths = {"payer", "payee"})
    List<Settlement> findByTrip_TripIdOrderBySettledAtDescSettlementIdDesc(Long tripId);
}
