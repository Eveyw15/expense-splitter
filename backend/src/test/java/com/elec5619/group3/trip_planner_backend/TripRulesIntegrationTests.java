package com.elec5619.group3.trip_planner_backend;

import com.elec5619.group3.trip_planner_backend.dto.*;
import com.elec5619.group3.trip_planner_backend.entity.*;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.repository.*;
import com.elec5619.group3.trip_planner_backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FR-3 / FR-4: rules around editing, deleting and leaving trips.
 */
@SpringBootTest(properties = "app.scheduling.sample-data=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TripRulesIntegrationTests {
    @Autowired TripMemberService memberService;
    @Autowired TripService tripService;
    @Autowired UpdateHistoryRepository historyRepository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired TripMemberRepository memberships;
    @Autowired TripRepository trips;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    User owner, member;
    Trip trip;

    @BeforeEach void setup() {
        historyRepository.deleteAll();
        settlementRepository.deleteAll();
        expenseRepository.deleteAll(); memberships.deleteAll(); trips.deleteAll(); users.deleteAll();
        owner = user("Owner"); member = user("Member");
        trip = new Trip(); trip.setName("Tokyo"); trip.setDestination("Japan");
        trip.setStartDate(LocalDate.of(2026, 12, 1)); trip.setEndDate(LocalDate.of(2026, 12, 10));
        trip.setPlannedBudget(new BigDecimal("1000.00"));
        trip = trips.save(trip);
        join(owner, "OWNER"); join(member, "MEMBER");
    }
    User user(String name) {
        User u = new User(); u.setUsername(name); u.setEmail(name.toLowerCase() + "@example.test"); u.setPasswordHash("test-only");
        return users.save(u);
    }
    void join(User u, String role) {
        TripMember m = new TripMember(); m.setTrip(trip); m.setUser(u); m.setRole(role); memberships.save(m);
    }
    Long membershipId(User u) {
        return memberships.findByTrip_TripIdAndUser_UserId(trip.getTripId(), u.getUserId()).orElseThrow().getTripMemberId();
    }
    UsernamePasswordAuthenticationToken auth(User u) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(u.getUserId(), u.getEmail()), null, List.of());
    }
    String tripJson(String budget, String extra) {
        return "{\"name\":\"Trip\",\"destination\":\"Kyoto\",\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-10\",\"plannedBudget\":" + budget + extra + "}";
    }



    @Test void editingATripCannotChangeItsStatus() throws Exception {
        for (String status : List.of("COMPLETED", "BANANA")) {
            mvc.perform(put("/api/trips/" + trip.getTripId()).with(authentication(auth(owner)))
                            .contentType(MediaType.APPLICATION_JSON).content(tripJson("500", ",\"status\":\"" + status + "\"")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PLANNING"))
                    .andExpect(jsonPath("$.destination").value("Kyoto"));
        }
        assertEquals("ONGOING", tripService.publishTrip(trip.getTripId(), owner.getUserId()).getStatus());
    }


    @Test void plannedBudgetMustFitTheDatabaseColumn() throws Exception {
        mvc.perform(post("/api/trips").with(authentication(auth(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content(tripJson("99999999.99", "")))
                .andExpect(status().isCreated());
        for (String budget : List.of("100000000", "1e30", "10.999")) {
            mvc.perform(post("/api/trips").with(authentication(auth(owner)))
                            .contentType(MediaType.APPLICATION_JSON).content(tripJson(budget, "")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Planned budget must be at most 99,999,999.99")));
            mvc.perform(put("/api/trips/" + trip.getTripId()).with(authentication(auth(owner)))
                            .contentType(MediaType.APPLICATION_JSON).content(tripJson(budget, "")))
                    .andExpect(status().isBadRequest());
        }
    }
}
