package com.elec5619.group3.trip_planner_backend.repository;

import com.elec5619.group3.trip_planner_backend.entity.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    @EntityGraph(attributePaths = {"payer", "createdBy", "trip", "shares.user"})
    List<Expense> findByTrip_TripIdOrderByDateDescExpenseIdDesc(Long tripId);
    @EntityGraph(attributePaths = {"payer", "createdBy", "trip", "shares.user"})
    Optional<Expense> findByExpenseIdAndTrip_TripId(Long expenseId, Long tripId);
}
