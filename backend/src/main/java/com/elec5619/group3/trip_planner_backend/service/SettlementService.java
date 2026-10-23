package com.elec5619.group3.trip_planner_backend.service;

import com.elec5619.group3.trip_planner_backend.dto.*;
import com.elec5619.group3.trip_planner_backend.entity.Settlement;
import com.elec5619.group3.trip_planner_backend.repository.SettlementRepository;
import com.elec5619.group3.trip_planner_backend.repository.TripRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SettlementService {
    private final SettlementRepository settlements;
    private final TripRepository trips;
    private final TripMemberService members;
    private final BalanceService balances;
    private final Validator validator;

    public SettlementService(SettlementRepository settlements, TripRepository trips, TripMemberService members,
                             BalanceService balances, Validator validator) {
        this.settlements = settlements;
        this.trips = trips;
        this.members = members;
        this.balances = balances;
        this.validator = validator;
    }

    public List<SettlementResponse> list(Long tripId, Long userId) {
        members.verifyUserIsMember(tripId, userId);
        return settlements.findByTrip_TripIdOrderBySettledAtDescSettlementIdDesc(tripId).stream()
                .map(this::response).toList();
    }

    @Transactional
    public SettlementResponse create(Long tripId, SettlementRequest request, Long userId) {
        // Acquire before any snapshot reads so a waiting payment sees the preceding committed payment.
        trips.lockFinancialWrites(tripId);
        var payer = members.verifyUserIsMember(tripId, userId);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
        if (userId.equals(request.getPayeeId())) throw new IllegalArgumentException("You cannot pay yourself");
        var payee = members.verifyUserIsMember(tripId, request.getPayeeId());
        BigDecimal outstanding = balances.get(tripId, userId).getYouOwe().stream()
                .filter(o -> o.getUserId().equals(request.getPayeeId())).map(BalanceResponse.Obligation::getAmount)
                .findFirst().orElse(BigDecimal.ZERO);
        if (outstanding.signum() <= 0) throw new IllegalArgumentException("You do not owe this member an outstanding payment");
        if (request.getAmount().compareTo(outstanding) > 0)
            throw new IllegalArgumentException("Payment cannot exceed the outstanding amount of " + outstanding);
        Settlement payment = new Settlement();
        payment.setTrip(payer.getTrip());
        payment.setPayer(payer.getUser());
        payment.setPayee(payee.getUser());
        payment.setAmount(request.getAmount().setScale(2));
        return response(settlements.saveAndFlush(payment));
    }

    private SettlementResponse response(Settlement payment) {
        return new SettlementResponse(payment.getSettlementId(), payment.getPayer().getUserId(),
                payment.getPayer().getUsername(), payment.getPayee().getUserId(), payment.getPayee().getUsername(),
                payment.getAmount(), payment.getSettledAt());
    }
}
