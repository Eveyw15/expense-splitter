package com.elec5619.group3.trip_planner_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Getter
@Setter
public class Expense {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_id")
    private Long expenseId;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payer_id", nullable = false)
    private User payer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;
    @Column(nullable = false, length = 200)
    private String description;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    // amount remains the frozen trip-base accounting value.
    @Column(name = "original_amount", precision = 10, scale = 2)
    private BigDecimal originalAmount;
    @Column(name = "currency_code", length = 3)
    private String currencyCode;
    @Column(name = "exchange_rate_to_base", precision = 24, scale = 12)
    private BigDecimal exchangeRateToBase;
    @Column(name = "exchange_rate_date")
    private LocalDate exchangeRateDate;
    public BigDecimal getOriginalAmount() { return originalAmount == null ? amount : originalAmount; }
    public String getCurrencyCode() { return currencyCode == null ? trip.getBaseCurrency() : currencyCode; }
    public BigDecimal getExchangeRateToBase() { return exchangeRateToBase == null ? BigDecimal.ONE : exchangeRateToBase; }
    public LocalDate getExchangeRateDate() { return exchangeRateDate == null ? date : exchangeRateDate; }
    @Column(nullable = false)
    private LocalDate date;
    @Column(length = 50)
    private String category;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("user.userId ASC")
    private java.util.List<ExpenseShare> shares = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private java.util.List<ExpenseItem> items = new java.util.ArrayList<>();
    @OneToOne(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ExpenseReceipt receipt;
    @Column(name = "split_mode", length = 10)
    private String splitMode;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
