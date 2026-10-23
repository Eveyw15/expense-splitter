package com.elec5619.group3.trip_planner_backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "expense_items") @Getter @Setter
public class ExpenseItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;
    @Column(nullable = false, length = 200)
    private String name;
    private Integer quantity;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseItemAllocation> allocations = new ArrayList<>();
}
