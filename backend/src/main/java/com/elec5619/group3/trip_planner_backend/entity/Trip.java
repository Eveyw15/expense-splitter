package com.elec5619.group3.trip_planner_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trip_id")
    private Long tripId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "planned_budget", nullable = false, precision = 10, scale = 2)
    private BigDecimal plannedBudget;

    @Column(name = "base_currency", length = 3)
    private String baseCurrency = "AUD";

    public String getBaseCurrency() { return baseCurrency == null ? "AUD" : baseCurrency; }

    @Column(nullable = false, length = 20)
    private String status = "PLANNING";

    // FR-4: shareable code for joining by link/QR. Nullable so existing trips
    // (and code without this field) keep working; generated on first use.
    @Column(name = "invite_code", unique = true, length = 12)
    private String inviteCode;

    // FR-3: optional cover photo (a StoredImage id). Null shows the default illustration.
    @Column(name = "cover_image_id", length = 36)
    private String coverImageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}