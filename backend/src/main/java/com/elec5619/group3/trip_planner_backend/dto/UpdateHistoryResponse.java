package com.elec5619.group3.trip_planner_backend.dto;

import java.time.LocalDateTime;

public record UpdateHistoryResponse(Long logId, Long tripId, Long changedById, String changedByUsername,
                                    String changeType, String description, LocalDateTime changedAt) {}
