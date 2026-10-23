package com.elec5619.group3.trip_planner_backend.service;

import com.elec5619.group3.trip_planner_backend.dto.CreateTripRequest;
import com.elec5619.group3.trip_planner_backend.dto.TripResponse;
import com.elec5619.group3.trip_planner_backend.dto.UpdateTripRequest;
import com.elec5619.group3.trip_planner_backend.entity.StoredImage;
import com.elec5619.group3.trip_planner_backend.entity.Trip;
import com.elec5619.group3.trip_planner_backend.entity.TripMember;
import com.elec5619.group3.trip_planner_backend.entity.User;
import com.elec5619.group3.trip_planner_backend.exception.ResourceNotFoundException;
import com.elec5619.group3.trip_planner_backend.repository.ExpenseRepository;
import com.elec5619.group3.trip_planner_backend.repository.PersonalBudgetRepository;
import com.elec5619.group3.trip_planner_backend.repository.SettlementRepository;
import com.elec5619.group3.trip_planner_backend.repository.TripMemberRepository;
import com.elec5619.group3.trip_planner_backend.repository.TripRepository;
import com.elec5619.group3.trip_planner_backend.repository.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TripService {

    private final PersonalBudgetRepository personalBudgets;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final UpdateHistoryService updateHistoryService;
    private final ImageService imageService;

    public TripService(
            TripRepository tripRepository,
            TripMemberRepository tripMemberRepository,
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            SettlementRepository settlementRepository,
            UpdateHistoryService updateHistoryService,
            ImageService imageService,
            PersonalBudgetRepository personalBudgets) {

        this.personalBudgets = personalBudgets;
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.updateHistoryService = updateHistoryService;
        this.imageService = imageService;
    }

    /**
     * FR-3 & FR-4:
     * Creates a new trip and automatically assigns
     * the creator as OWNER.
     */
    @Transactional
    public TripResponse createTrip(
            CreateTripRequest request,
            Long userId) {

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException(
                    "End date cannot be before start date"
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found")
                );

        Trip trip = new Trip();
        trip.setName(request.getName());
        trip.setDestination(request.getDestination());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setPlannedBudget(request.getPlannedBudget());

        trip.setBaseCurrency(CurrencyService.validateCode(request.getBaseCurrency() == null ? "AUD" : request.getBaseCurrency()));
        Trip savedTrip = tripRepository.save(trip);

        TripMember ownerMember = new TripMember();
        ownerMember.setTrip(savedTrip);
        ownerMember.setUser(user);
        ownerMember.setRole("OWNER");

        tripMemberRepository.save(ownerMember);

        return mapToResponse(savedTrip, "OWNER");
    }

    /**
     * FR-3:
     * Get all active trips the logged-in user belongs to.
     */
    @Transactional(readOnly = true)
    public List<TripResponse> getUserTrips(Long userId) {

        List<TripMember> memberships =
                tripMemberRepository.findByUser_UserId(userId);

        return memberships.stream()
                .filter(TripMember::isActive)
                .map(member ->
                        mapToResponse(
                                member.getTrip(),
                                member.getRole()
                        )
                )
                .collect(Collectors.toList());
    }

    /**
     * FR-3:
     * Get a specific trip overview.
     * Allowed: active trip members only.
     */
    @Transactional(readOnly = true)
    public TripResponse getTripById(
            Long tripId,
            Long userId) {

        if (!tripRepository.existsById(tripId)) {
            throw new ResourceNotFoundException(
                    "Trip not found"
            );
        }

        TripMember membership =
                tripMemberRepository
                        .findByTrip_TripIdAndUser_UserId(
                                tripId,
                                userId
                        )
                        .filter(TripMember::isActive)
                        .orElseThrow(() ->
                                new AccessDeniedException(
                                        "You are not a member of this trip"
                                )
                        );

        return mapToResponse(
                membership.getTrip(),
                membership.getRole()
        );
    }

    /**
     * FR-3:
     * Update trip details.
     * Allowed: OWNER only.
     */
    @Transactional
    public TripResponse updateTrip(
            Long tripId,
            UpdateTripRequest request,
            Long userId) {

        TripMember membership =
                requireOwner(tripId, userId);

        if (request.getEndDate()
                .isBefore(request.getStartDate())) {

            throw new IllegalArgumentException(
                    "End date cannot be before start date"
            );
        }

        Trip trip = membership.getTrip();

        trip.setName(request.getName());
        trip.setDestination(request.getDestination());
        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setPlannedBudget(request.getPlannedBudget());

        // Status is not editable here: it only moves through publishTrip / archiveTrip.

        Trip updatedTrip =
                tripRepository.save(trip);

        return mapToResponse(
                updatedTrip,
                membership.getRole()
        );
    }

    /**
     * FR-2:
     * Publish a trip.
     * PLANNING -> ONGOING.
     */
    @Transactional
    public TripResponse publishTrip(
            Long tripId,
            Long userId) {

        TripMember membership =
                requireOwner(tripId, userId);

        Trip trip = membership.getTrip();

        if (!"PLANNING".equals(trip.getStatus())) {
            throw new IllegalArgumentException(
                    "Only a trip in PLANNING can be published"
            );
        }

        trip.setStatus("ONGOING");

        Trip updatedTrip =
                tripRepository.save(trip);

        return mapToResponse(
                updatedTrip,
                membership.getRole()
        );
    }

    /**
     * FR-2 / FR-20:
     * Archive a trip.
     * ONGOING -> COMPLETED.
     */
    @Transactional
    public TripResponse archiveTrip(
            Long tripId,
            Long userId) {

        TripMember membership =
                requireOwner(tripId, userId);

        Trip trip = membership.getTrip();

        if (!"ONGOING".equals(trip.getStatus())) {
            throw new IllegalArgumentException(
                    "Only an ONGOING trip can be archived"
            );
        }

        trip.setStatus("COMPLETED");

        Trip updatedTrip =
                tripRepository.save(trip);

        return mapToResponse(
                updatedTrip,
                membership.getRole()
        );
    }


    /**
     * FR-3:
     * Set or replace the trip's cover photo.
     * Allowed: OWNER only.
     */
    @Transactional
    public TripResponse updateCoverImage(
            Long tripId,
            MultipartFile file,
            Long userId) {

        TripMember membership =
                requireOwner(tripId, userId);

        Trip trip = membership.getTrip();
        String previousImageId = trip.getCoverImageId();

        StoredImage image = imageService.store(file);
        trip.setCoverImageId(image.getImageId());

        Trip updatedTrip =
                tripRepository.save(trip);

        imageService.delete(previousImageId);

        return mapToResponse(
                updatedTrip,
                membership.getRole()
        );
    }

    /**
     * FR-3:
     * Remove the trip's cover photo (falls back to the default illustration).
     * Allowed: OWNER only.
     */
    @Transactional
    public TripResponse removeCoverImage(
            Long tripId,
            Long userId) {

        TripMember membership =
                requireOwner(tripId, userId);

        Trip trip = membership.getTrip();
        String previousImageId = trip.getCoverImageId();

        trip.setCoverImageId(null);

        Trip updatedTrip =
                tripRepository.save(trip);

        imageService.delete(previousImageId);

        return mapToResponse(
                updatedTrip,
                membership.getRole()
        );
    }

    /**
     * FR-3 & FR-4:
     * Delete a trip.
     * Allowed: OWNER only.
     */
    @Transactional
    public void deleteTrip(
            Long tripId,
            Long userId) {

        String coverImageId =
                requireOwner(tripId, userId)
                        .getTrip()
                        .getCoverImageId();

        /*
         * Prevent concurrent financial writes while the
         * trip and its financial records are being deleted.
         */
        tripRepository.lockFinancialWrites(tripId);

        /*
         * Remove settlements first.
         */
        settlementRepository.deleteAll(
                settlementRepository
                        .findByTrip_TripIdOrderBySettledAtDescSettlementIdDesc(
                                tripId
                        )
        );

        /*
         * Remove expenses associated with the trip.
         */
        expenseRepository.deleteAll(
                expenseRepository
                        .findByTrip_TripIdOrderByDateDescExpenseIdDesc(
                                tripId
                        )
        );

        personalBudgets.deleteByTrip_TripId(tripId);

        /* Remove trip memberships. */
        List<TripMember> members =
                tripMemberRepository
                        .findByTrip_TripId(tripId);

        tripMemberRepository.deleteAll(members);

        tripRepository.deleteById(tripId);

        imageService.delete(coverImageId);
    }

    /**
     * Shared OWNER authorization helper.
     */
    private TripMember requireOwner(
            Long tripId,
            Long userId) {

        if (!tripRepository.existsById(tripId)) {
            throw new ResourceNotFoundException(
                    "Trip not found"
            );
        }

        TripMember membership =
                tripMemberRepository
                        .findByTrip_TripIdAndUser_UserId(
                                tripId,
                                userId
                        )
                        .filter(TripMember::isActive)
                        .orElseThrow(() ->
                                new AccessDeniedException(
                                        "You are not a member of this trip"
                                )
                        );

        if (!"OWNER".equals(membership.getRole())) {
            throw new AccessDeniedException(
                    "Only the trip owner can perform this action"
            );
        }

        return membership;
    }

    /**
     * Convert Trip entity into TripResponse DTO.
     */
    private TripResponse mapToResponse(
            Trip trip,
            String role) {

        return TripResponse.builder()
                .tripId(trip.getTripId())
                .name(trip.getName())
                .destination(trip.getDestination())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .plannedBudget(trip.getPlannedBudget())
                .baseCurrency(trip.getBaseCurrency())
                .status(trip.getStatus())
                .role(role)
                .createdAt(trip.getCreatedAt())
                .coverImageId(trip.getCoverImageId())
                .build();
    }
}
