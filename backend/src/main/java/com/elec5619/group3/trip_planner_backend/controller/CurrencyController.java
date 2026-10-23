package com.elec5619.group3.trip_planner_backend.controller;

import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.service.CurrencyService;
import com.elec5619.group3.trip_planner_backend.service.TripMemberService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trips/{tripId}/currency-rate")
public class CurrencyController {
    private final CurrencyService currencies;
    private final TripMemberService members;
    public CurrencyController(CurrencyService currencies, TripMemberService members) {
        this.currencies = currencies; this.members = members;
    }
    @GetMapping
    public CurrencyService.Quote displayRate(@PathVariable Long tripId, @RequestParam String to,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        var member = members.verifyUserIsMember(tripId, user.getUserId());
        return currencies.getRate(member.getTrip().getBaseCurrency(), to, null);
    }
}
