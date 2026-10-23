package com.elec5619.group3.trip_planner_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "expense_shares", uniqueConstraints = @UniqueConstraint(columnNames = {"expense_id", "user_id"}))
@Getter
@Setter
public class ExpenseShare {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_share_id")
    private Long expenseShareId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "share_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal shareAmount;
    @Column(name = "original_share_amount", precision = 10, scale = 2)
    private BigDecimal originalShareAmount;
    public BigDecimal getOriginalShareAmount() { return originalShareAmount == null ? shareAmount : originalShareAmount; }
}
