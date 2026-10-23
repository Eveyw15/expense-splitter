package com.elec5619.group3.trip_planner_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlements")
@Getter
@Setter
public class Settlement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    private Long settlementId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payer_id", nullable = false)
    private User payer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payee_id", nullable = false)
    private User payee;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    @Column(name = "settled_at", nullable = false, updatable = false)
    private LocalDateTime settledAt;

    @PrePersist
    protected void onCreate() { settledAt = LocalDateTime.now(); }
}
