package com.elec5619.group3.trip_planner_backend.service;

import com.elec5619.group3.trip_planner_backend.dto.*;
import com.elec5619.group3.trip_planner_backend.entity.PersonalBudget;
import com.elec5619.group3.trip_planner_backend.repository.PersonalBudgetRepository;
import com.elec5619.group3.trip_planner_backend.repository.TripRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PersonalBudgetService {
    private final PersonalBudgetRepository budgets;
    private final TripRepository trips;
    private final TripMemberService members;
    private final CurrencyService currencies;
    private final Validator validator;
    public PersonalBudgetService(PersonalBudgetRepository budgets, TripRepository trips, TripMemberService members,
                                 CurrencyService currencies, Validator validator) {
        this.budgets = budgets; this.trips = trips; this.members = members; this.currencies = currencies; this.validator = validator;
    }
    public PersonalBudgetResponse get(Long tripId, Long userId) {
        members.verifyUserIsMember(tripId, userId);
        return budgets.findByTrip_TripIdAndUser_UserId(tripId, userId).map(this::response).orElse(null);
    }
    @Transactional
    public PersonalBudgetResponse save(Long tripId, Long userId, PersonalBudgetRequest request) {
        trips.lockFinancialWrites(tripId);
        var member = members.verifyUserIsMember(tripId, userId);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
        var quote = currencies.getRate(request.currencyCode(), member.getTrip().getBaseCurrency(), null);
        var base = CurrencyService.baseAmount(request.amount(), quote.rate());
        PersonalBudget budget = budgets.findByTrip_TripIdAndUser_UserId(tripId, userId).orElseGet(PersonalBudget::new);
        budget.setTrip(member.getTrip()); budget.setUser(member.getUser());
        budget.setAmount(request.amount().setScale(2)); budget.setCurrencyCode(request.currencyCode());
        budget.setExchangeRateToBase(quote.rate()); budget.setExchangeRateDate(quote.date()); budget.setBaseAmount(base);
        return response(budgets.saveAndFlush(budget));
    }
    private PersonalBudgetResponse response(PersonalBudget b) {
        return new PersonalBudgetResponse(b.getAmount(), b.getCurrencyCode(), b.getExchangeRateToBase(), b.getBaseAmount(), b.getExchangeRateDate(), b.getUpdatedAt());
    }
}
