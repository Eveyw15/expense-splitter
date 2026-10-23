package com.elec5619.group3.trip_planner_backend;

import com.elec5619.group3.trip_planner_backend.dto.*;
import com.elec5619.group3.trip_planner_backend.entity.*;
import com.elec5619.group3.trip_planner_backend.exception.ResourceNotFoundException;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.repository.*;
import com.elec5619.group3.trip_planner_backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FR-4: Joining a trip with a shareable invite code (link / QR).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TripInviteCodeIntegrationTests {
    @Autowired TripMemberService memberService;
    @Autowired TripService tripService;
    @Autowired UpdateHistoryRepository historyRepository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired TripMemberRepository memberships;
    @Autowired TripRepository trips;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    User owner, member, stranger;
    Trip trip;

    @BeforeEach void setup() {
        historyRepository.deleteAll();
        settlementRepository.deleteAll();
        expenseRepository.deleteAll(); memberships.deleteAll(); trips.deleteAll(); users.deleteAll();
        owner = user("Owner"); member = user("Member"); stranger = user("Stranger");
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
    String code(User by) { return memberService.getInviteCode(trip.getTripId(), by.getUserId()).getInviteCode(); }
    long membershipRows(User u) {
        return memberships.findByTrip_TripId(trip.getTripId()).stream().filter(m -> m.getUser().getUserId().equals(u.getUserId())).count();
    }
    UsernamePasswordAuthenticationToken auth(User u) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(u.getUserId(), u.getEmail()), null, List.of());
    }

    @Test void membersShareOneStableCodeThatIsHardToGuess() {
        String code = code(owner);
        assertTrue(code.matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}"), code);
        assertEquals(code, code(member));
        assertEquals(code, code(owner));
    }

    @Test void outsidersAndPendingInviteesCannotSeeTheCode() {
        assertThrows(AccessDeniedException.class, () -> code(stranger));
        InviteMemberRequest r = new InviteMemberRequest(); r.setEmail(stranger.getEmail());
        memberService.inviteMember(trip.getTripId(), r, owner.getUserId());
        assertThrows(AccessDeniedException.class, () -> code(stranger));
    }

    @Test void previewShowsTripWithoutJoining() {
        TripInvitePreviewResponse preview = memberService.previewTripByInviteCode(code(owner), stranger.getUserId());
        assertEquals("Tokyo", preview.getName());
        assertEquals("Japan", preview.getDestination());
        assertEquals(2, preview.getMemberCount());
        assertFalse(preview.isAlreadyMember());
        assertTrue(memberService.previewTripByInviteCode(code(owner), member.getUserId()).isAlreadyMember());
        assertEquals(0, membershipRows(stranger));
    }

    @Test void joiningAddsAnActiveMemberImmediately() {
        TripMemberResponse joined = memberService.joinTripByInviteCode(code(owner), stranger.getUserId());
        assertEquals("JOINED", joined.getInviteStatus());
        assertEquals("MEMBER", joined.getRole());
        assertEquals(trip.getTripId(), joined.getTripId());
        assertEquals("MEMBER", tripService.getTripById(trip.getTripId(), stranger.getUserId()).getRole());
        assertEquals(1, tripService.getUserTrips(stranger.getUserId()).size());
    }

    @Test void codesAreMatchedIgnoringCaseSpacesAndDashes() {
        String code = code(owner);
        String typed = " " + code.substring(0, 4).toLowerCase() + "-" + code.substring(4).toLowerCase() + " ";
        assertEquals(trip.getTripId(), memberService.previewTripByInviteCode(typed, stranger.getUserId()).getTripId());
        assertEquals("JOINED", memberService.joinTripByInviteCode(typed, stranger.getUserId()).getInviteStatus());
    }

    @Test void joiningTwiceDoesNotDuplicateMembership() {
        String code = code(owner);
        Long first = memberService.joinTripByInviteCode(code, stranger.getUserId()).getTripMemberId();
        Long second = memberService.joinTripByInviteCode(code, stranger.getUserId()).getTripMemberId();
        assertEquals(first, second);
        assertEquals(1, membershipRows(stranger));
        assertEquals("OWNER", memberService.joinTripByInviteCode(code, owner.getUserId()).getRole());
    }

    @Test void joiningWithCodeAcceptsAnOutstandingEmailInvite() {
        InviteMemberRequest r = new InviteMemberRequest(); r.setEmail(stranger.getEmail());
        Long inviteId = memberService.inviteMember(trip.getTripId(), r, owner.getUserId()).getTripMemberId();

        TripMemberResponse joined = memberService.joinTripByInviteCode(code(owner), stranger.getUserId());
        assertEquals(inviteId, joined.getTripMemberId());
        assertEquals("JOINED", joined.getInviteStatus());
        assertEquals(1, membershipRows(stranger));
        assertTrue(memberService.getMyPendingInvitations(stranger.getUserId()).isEmpty());
    }

    @Test void ownerCanResetCodeAndOldCodeStopsWorking() {
        String old = code(owner);
        assertThrows(AccessDeniedException.class, () -> memberService.regenerateInviteCode(trip.getTripId(), member.getUserId()));

        String fresh = memberService.regenerateInviteCode(trip.getTripId(), owner.getUserId()).getInviteCode();
        assertNotEquals(old, fresh);
        assertEquals(fresh, code(member));
        assertThrows(ResourceNotFoundException.class, () -> memberService.previewTripByInviteCode(old, stranger.getUserId()));
        assertThrows(ResourceNotFoundException.class, () -> memberService.joinTripByInviteCode(old, stranger.getUserId()));
        assertEquals("JOINED", memberService.joinTripByInviteCode(fresh, stranger.getUserId()).getInviteStatus());
    }

    @Test void unknownOrBlankCodesAreNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> memberService.joinTripByInviteCode("NOTACODE", stranger.getUserId()));
        assertThrows(ResourceNotFoundException.class, () -> memberService.previewTripByInviteCode("  -- ", stranger.getUserId()));
    }

    @Test void completedTripsCannotBeJoined() {
        String code = code(owner);
        Trip saved = trips.findById(trip.getTripId()).orElseThrow();
        saved.setStatus("COMPLETED"); trips.save(saved);
        assertThrows(IllegalStateException.class, () -> memberService.joinTripByInviteCode(code, stranger.getUserId()));
        assertEquals(0, membershipRows(stranger));
    }

    @Test void inviteCodeEndpointsReturnExpectedStatuses() throws Exception {
        String base = "/api/trips/" + trip.getTripId() + "/members/invite-code";
        mvc.perform(get(base).with(authentication(auth(member))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inviteCode").isString());
        mvc.perform(get(base).with(authentication(auth(stranger)))).andExpect(status().isForbidden());
        mvc.perform(post(base + "/regenerate").with(authentication(auth(member)))).andExpect(status().isForbidden());
        mvc.perform(post(base + "/regenerate").with(authentication(auth(owner)))).andExpect(status().isOk());

        String code = code(owner);
        mvc.perform(get("/api/trips/join/" + code).with(authentication(auth(stranger))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tokyo"))
                .andExpect(jsonPath("$.alreadyMember").value(false));
        mvc.perform(post("/api/trips/join/" + code).with(authentication(auth(stranger))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getTripId()));
        mvc.perform(get("/api/trips/join/NOTACODE").with(authentication(auth(stranger)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/trips/join/" + code)).andExpect(status().isUnauthorized());
    }
}
