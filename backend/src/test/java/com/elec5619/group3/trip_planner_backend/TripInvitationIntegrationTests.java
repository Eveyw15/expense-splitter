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
 * FR-4: Invited users must accept before they join a trip, and may decline.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TripInvitationIntegrationTests {
    @Autowired TripMemberService memberService;
    @Autowired TripService tripService;
    @Autowired UpdateHistoryRepository historyRepository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired TripMemberRepository memberships;
    @Autowired TripRepository trips;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    User owner, member, invitee, stranger;
    Trip trip;

    @BeforeEach void setup() {
        historyRepository.deleteAll();
        settlementRepository.deleteAll();
        expenseRepository.deleteAll(); memberships.deleteAll(); trips.deleteAll(); users.deleteAll();
        owner = user("Owner"); member = user("Member"); invitee = user("Invitee"); stranger = user("Stranger");
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
    TripMemberResponse invite(User who, User by) {
        InviteMemberRequest r = new InviteMemberRequest(); r.setEmail(who.getEmail());
        return memberService.inviteMember(trip.getTripId(), r, by.getUserId());
    }
    TripMemberResponse respond(Long inviteId, String action, User by) {
        RespondInviteRequest r = new RespondInviteRequest(); r.setAction(action);
        return memberService.respondToInvite(inviteId, r, by.getUserId());
    }
    List<Long> memberIds() {
        return memberService.getTripMembers(trip.getTripId(), owner.getUserId()).stream().map(TripMemberResponse::getUserId).toList();
    }
    UsernamePasswordAuthenticationToken auth(User u) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(u.getUserId(), u.getEmail()), null, List.of());
    }

    @Test void invitedUserIsPendingAndHasNoAccessUntilAccepting() {
        TripMemberResponse pending = invite(invitee, member);
        assertEquals("PENDING", pending.getInviteStatus());

        assertFalse(memberIds().contains(invitee.getUserId()));
        assertEquals(List.of(invitee.getUserId()), memberService.getPendingTripInvites(trip.getTripId(), owner.getUserId())
                .stream().map(TripMemberResponse::getUserId).toList());
        assertTrue(tripService.getUserTrips(invitee.getUserId()).isEmpty());
        assertThrows(AccessDeniedException.class, () -> tripService.getTripById(trip.getTripId(), invitee.getUserId()));
        assertThrows(AccessDeniedException.class, () -> memberService.getTripMembers(trip.getTripId(), invitee.getUserId()));

        List<TripMemberResponse> inbox = memberService.getMyPendingInvitations(invitee.getUserId());
        assertEquals(1, inbox.size());
        assertEquals("Tokyo", inbox.get(0).getTripName());
        assertEquals("Japan", inbox.get(0).getTripDestination());
    }

    @Test void acceptingJoinsTheTrip() {
        Long inviteId = invite(invitee, owner).getTripMemberId();
        TripMemberResponse joined = respond(inviteId, "ACCEPT", invitee);

        assertEquals("JOINED", joined.getInviteStatus());
        assertTrue(memberIds().contains(invitee.getUserId()));
        assertEquals("MEMBER", tripService.getTripById(trip.getTripId(), invitee.getUserId()).getRole());
        assertEquals(1, tripService.getUserTrips(invitee.getUserId()).size());
        assertTrue(memberService.getMyPendingInvitations(invitee.getUserId()).isEmpty());
        assertTrue(memberService.getPendingTripInvites(trip.getTripId(), owner.getUserId()).isEmpty());
    }

    @Test void rejectingRemovesTheInviteAndAllowsReinvite() {
        Long inviteId = invite(invitee, owner).getTripMemberId();
        assertNull(respond(inviteId, "REJECT", invitee));

        assertFalse(memberships.existsById(inviteId));
        assertFalse(memberIds().contains(invitee.getUserId()));
        assertTrue(memberService.getMyPendingInvitations(invitee.getUserId()).isEmpty());
        assertEquals("PENDING", invite(invitee, owner).getInviteStatus());
    }

    @Test void onlyTheInviteeCanRespondAndOnlyOnce() {
        Long inviteId = invite(invitee, owner).getTripMemberId();
        assertThrows(AccessDeniedException.class, () -> respond(inviteId, "ACCEPT", stranger));
        assertThrows(AccessDeniedException.class, () -> respond(inviteId, "ACCEPT", owner));

        respond(inviteId, "ACCEPT", invitee);
        IllegalStateException again = assertThrows(IllegalStateException.class, () -> respond(inviteId, "REJECT", invitee));
        assertEquals("Invitation has already been responded to", again.getMessage());
    }

    @Test void duplicateInvitesAreRejectedWithClearMessages() {
        invite(invitee, owner);
        assertEquals("User has already been invited to this trip",
                assertThrows(IllegalArgumentException.class, () -> invite(invitee, member)).getMessage());
        assertEquals("User is already a member of this trip",
                assertThrows(IllegalArgumentException.class, () -> invite(member, owner)).getMessage());
    }

    @Test void pendingInviteCannotBePromotedButOwnerCanCancelIt() {
        Long inviteId = invite(invitee, member).getTripMemberId();
        UpdateMemberRoleRequest promote = new UpdateMemberRoleRequest(); promote.setRole("OWNER");
        assertThrows(IllegalStateException.class,
                () -> memberService.updateMemberRole(trip.getTripId(), inviteId, promote, owner.getUserId()));

        assertThrows(AccessDeniedException.class, () -> memberService.removeMember(trip.getTripId(), inviteId, member.getUserId()));
        memberService.removeMember(trip.getTripId(), inviteId, owner.getUserId());
        assertTrue(memberService.getMyPendingInvitations(invitee.getUserId()).isEmpty());
    }


    @Test void invitationEndpointsReturnExpectedStatuses() throws Exception {
        Long inviteId = invite(invitee, owner).getTripMemberId();
        mvc.perform(get("/api/trips/invitations").with(authentication(auth(invitee))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tripMemberId").value(inviteId))
                .andExpect(jsonPath("$[0].tripName").value("Tokyo"));
        mvc.perform(get("/api/trips/" + trip.getTripId() + "/members/pending").with(authentication(auth(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("invitee@example.test"));
        mvc.perform(get("/api/trips/" + trip.getTripId() + "/members/pending").with(authentication(auth(invitee))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/trips/invitations/" + inviteId + "/respond").with(authentication(auth(invitee)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips/invitations/" + inviteId + "/respond").with(authentication(auth(stranger)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/trips/invitations/" + inviteId + "/respond").with(authentication(auth(invitee)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\"}"))
                .andExpect(status().isNoContent());

        Long secondId = invite(invitee, owner).getTripMemberId();
        mvc.perform(post("/api/trips/invitations/" + secondId + "/respond").with(authentication(auth(invitee)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteStatus").value("JOINED"));
    }
}
