package com.elec5619.group3.trip_planner_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "personal_budgets", uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "user_id"}))
@Getter @Setter
public class PersonalBudget {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;
    @Column(name = "exchange_rate_to_base", nullable = false, precision = 24, scale = 12)
    private BigDecimal exchangeRateToBase;
    @Column(name = "base_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseAmount;
    @Column(name = "exchange_rate_date", nullable = false)
    private LocalDate exchangeRateDate;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist @PreUpdate
    void timestamp() { updatedAt = LocalDateTime.now(); }
}
