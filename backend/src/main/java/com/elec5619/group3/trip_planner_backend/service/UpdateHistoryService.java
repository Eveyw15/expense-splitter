package com.elec5619.group3.trip_planner_backend.service;

import com.elec5619.group3.trip_planner_backend.dto.UpdateHistoryResponse;
import com.elec5619.group3.trip_planner_backend.entity.Trip;
import com.elec5619.group3.trip_planner_backend.entity.UpdateHistory;
import com.elec5619.group3.trip_planner_backend.entity.User;
import com.elec5619.group3.trip_planner_backend.repository.TripRepository;
import com.elec5619.group3.trip_planner_backend.repository.UpdateHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class UpdateHistoryService {
    private final UpdateHistoryRepository repository;
    private final TripRepository tripRepository;
    private final TripMemberService memberService;

    public UpdateHistoryService(UpdateHistoryRepository repository, TripRepository tripRepository,
                                TripMemberService memberService) {
        this.repository = repository;
        this.tripRepository = tripRepository;
        this.memberService = memberService;
    }

    // Joins the caller's transaction: failed changes must not leave a history entry.
    @Transactional
    public void record(Trip trip, User actor, String type, String description) {
        UpdateHistory entry = new UpdateHistory();
        entry.setTrip(trip);
        entry.setChangedBy(actor);
        entry.setChangeType(type);
        entry.setDescription(description);
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<UpdateHistoryResponse> list(Long tripId, Long userId) {
        if (!tripRepository.existsById(tripId)) throw new NoSuchElementException("Trip not found");
        memberService.verifyUserIsMember(tripId, userId);
        return repository.findTop50ByTrip_TripIdOrderByChangedAtDescLogIdDesc(tripId).stream()
                .map(entry -> new UpdateHistoryResponse(entry.getLogId(), tripId,
                        entry.getChangedBy().getUserId(), entry.getChangedBy().getUsername(),
                        entry.getChangeType(), entry.getDescription(), entry.getChangedAt()))
                .toList();
    }
}
