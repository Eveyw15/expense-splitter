package com.elec5619.group3.trip_planner_backend.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class ExpenseResponse {
    private Long expenseId;
    private Long tripId;
    private Long payerId;
    private String payerName;
    private Long createdById;
    private String createdByName;
    private String description;
    private BigDecimal amount;
    private BigDecimal originalAmount;
    private String currencyCode;
    private String baseCurrency;
    private BigDecimal exchangeRateToBase;
    private LocalDate exchangeRateDate;
    private LocalDate date;
    private String category;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean canEdit;
    private BigDecimal currentUserShare;
    private java.util.List<Share> shares;
    private String splitMode;
    private java.util.List<Item> items;
    private Receipt receipt;

    public record Item(Long id, String name, Integer quantity, BigDecimal amount, java.util.List<Allocation> allocations) {}
    public record Allocation(Long userId, String username, BigDecimal shareAmount) {}
    public record Receipt(String fileName, String contentType, String uploadedBy, LocalDateTime uploadedAt) {}

    @Getter
    @lombok.AllArgsConstructor
    public static class Share {
        private Long userId;
        private String username;
        private BigDecimal shareAmount;
        private BigDecimal originalShareAmount;
    }
}
