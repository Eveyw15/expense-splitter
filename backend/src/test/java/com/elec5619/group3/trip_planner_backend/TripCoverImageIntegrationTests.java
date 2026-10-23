package com.elec5619.group3.trip_planner_backend;

import com.elec5619.group3.trip_planner_backend.dto.TripResponse;
import com.elec5619.group3.trip_planner_backend.entity.*;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.repository.*;
import com.elec5619.group3.trip_planner_backend.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FR-3: Trip cover photos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TripCoverImageIntegrationTests {
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @Autowired TripService tripService;
    @Autowired UpdateHistoryRepository historyRepository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired TripMemberRepository memberships;
    @Autowired TripRepository trips;
    @Autowired UserRepository users;
    @Autowired StoredImageRepository images;
    @Autowired MockMvc mvc;
    User owner, member, stranger;
    Trip trip;

    @BeforeEach void setup() {
        historyRepository.deleteAll();
        settlementRepository.deleteAll();
        expenseRepository.deleteAll(); memberships.deleteAll(); trips.deleteAll();
        users.deleteAll(); images.deleteAll();
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
    MockMultipartFile file(byte[] bytes, String contentType) {
        return new MockMultipartFile("file", "cover", contentType, bytes);
    }
    TripResponse upload(byte[] bytes, User by) {
        return tripService.updateCoverImage(trip.getTripId(), file(bytes, "image/jpeg"), by.getUserId());
    }
    UsernamePasswordAuthenticationToken auth(User u) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(u.getUserId(), u.getEmail()), null, List.of());
    }

    @Test void tripsHaveNoCoverByDefault() {
        assertNull(tripService.getTripById(trip.getTripId(), owner.getUserId()).getCoverImageId());
    }

    @Test void ownerUploadsCoverAndItLoadsWithoutLogin() throws Exception {
        String coverUrl = "/api/trips/" + trip.getTripId() + "/cover";
        String body = mvc.perform(multipart(coverUrl).file(file(JPEG, "image/jpeg")).with(authentication(auth(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverImageId").isString())
                .andReturn().getResponse().getContentAsString();
        String imageId = body.replaceAll(".*\"coverImageId\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/images/" + imageId))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test void membersSeeTheCoverInTheirTripList() {
        String imageId = upload(JPEG, owner).getCoverImageId();
        assertEquals(imageId, tripService.getUserTrips(member.getUserId()).get(0).getCoverImageId());
        assertEquals(imageId, tripService.getTripById(trip.getTripId(), member.getUserId()).getCoverImageId());
    }

    @Test void pngAndWebpAreAcceptedWithTheirRealType() {
        assertEquals("image/png", images.findById(upload(PNG, owner).getCoverImageId()).orElseThrow().getContentType());
        assertEquals("image/webp", images.findById(upload(WEBP, owner).getCoverImageId()).orElseThrow().getContentType());
    }

    @Test void replacingACoverDeletesTheOldImage() {
        String first = upload(JPEG, owner).getCoverImageId();
        String second = upload(PNG, owner).getCoverImageId();
        assertNotEquals(first, second);
        assertFalse(images.existsById(first));
        assertEquals(1, images.count());
    }

    @Test void removingACoverFallsBackToNone() throws Exception {
        String imageId = upload(JPEG, owner).getCoverImageId();
        assertNull(tripService.removeCoverImage(trip.getTripId(), owner.getUserId()).getCoverImageId());
        assertFalse(images.existsById(imageId));
        mvc.perform(get("/api/images/" + imageId)).andExpect(status().isNotFound());
        assertNull(tripService.removeCoverImage(trip.getTripId(), owner.getUserId()).getCoverImageId());
    }

    @Test void onlyOwnersCanChangeTheCover() throws Exception {
        assertThrows(AccessDeniedException.class, () -> upload(JPEG, member));
        assertThrows(AccessDeniedException.class, () -> upload(JPEG, stranger));
        String coverUrl = "/api/trips/" + trip.getTripId() + "/cover";
        mvc.perform(multipart(coverUrl).file(file(JPEG, "image/jpeg")).with(authentication(auth(member))))
                .andExpect(status().isForbidden());
        mvc.perform(delete(coverUrl).with(authentication(auth(member)))).andExpect(status().isForbidden());
        assertEquals(0, images.count());
    }

    @Test void nonImagesAreRejectedEvenIfTheyClaimToBeImages() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tripService.updateCoverImage(trip.getTripId(), file(svg, "image/jpeg"), owner.getUserId()));
        assertEquals("Image must be a JPEG, PNG or WebP file", ex.getMessage());

        String coverUrl = "/api/trips/" + trip.getTripId() + "/cover";
        mvc.perform(multipart(coverUrl).file(file(svg, "image/svg+xml")).with(authentication(auth(owner))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Image must be a JPEG, PNG or WebP file"));
        mvc.perform(multipart(coverUrl).file(file(new byte[0], "image/jpeg")).with(authentication(auth(owner))))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(coverUrl).with(authentication(auth(owner))))
                .andExpect(status().isBadRequest());
        assertEquals(0, images.count());
        assertNull(tripService.getTripById(trip.getTripId(), owner.getUserId()).getCoverImageId());
    }

    @Test void imagesOverFiveMegabytesAreRejected() {
        byte[] big = Arrays.copyOf(JPEG, StoredImage.MAX_BYTES + 1);
        assertEquals("Image must be 5 MB or smaller",
                assertThrows(IllegalArgumentException.class, () -> upload(big, owner)).getMessage());
        byte[] limit = Arrays.copyOf(JPEG, StoredImage.MAX_BYTES);
        assertNotNull(upload(limit, owner).getCoverImageId());
    }

    @Test void deletingATripDeletesItsCover() {
        String imageId = upload(JPEG, owner).getCoverImageId();
        tripService.deleteTrip(trip.getTripId(), owner.getUserId());
        assertFalse(images.existsById(imageId));
    }
}
