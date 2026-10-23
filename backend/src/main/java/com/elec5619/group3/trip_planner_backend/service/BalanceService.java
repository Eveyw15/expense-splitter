package com.elec5619.group3.trip_planner_backend.service;

import com.elec5619.group3.trip_planner_backend.dto.BalanceResponse;
import com.elec5619.group3.trip_planner_backend.dto.SimplifiedDebtResponse;
import com.elec5619.group3.trip_planner_backend.dto.BalanceResponse.Obligation;
import com.elec5619.group3.trip_planner_backend.entity.Expense;
import com.elec5619.group3.trip_planner_backend.entity.User;
import com.elec5619.group3.trip_planner_backend.repository.ExpenseRepository;
import com.elec5619.group3.trip_planner_backend.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class BalanceService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final ExpenseRepository expenses;
    private final SettlementRepository settlements;
    private final TripMemberService members;

    public BalanceService(ExpenseRepository expenses, SettlementRepository settlements, TripMemberService members) {
        this.expenses = expenses;
        this.settlements = settlements;
        this.members = members;
    }

    public BalanceResponse get(Long tripId, Long userId) {
        members.verifyUserIsMember(tripId, userId);
        return calculate(tripId, userId, expenses.findByTrip_TripIdOrderByDateDescExpenseIdDesc(tripId));
    }

    // Callers verify membership. Summary reuses its already fetched expenses.
    BalanceResponse calculate(Long tripId, Long userId, List<Expense> records) {
        Map<Long, BigDecimal> pairs = new TreeMap<>();
        Map<Long, String> names = new HashMap<>();
        for (Expense expense : records) {
            for (var share : expense.getShares()) {
                if (!share.getUser().getUserId().equals(expense.getPayer().getUserId()))
                    add(pairs, names, userId, share.getUser(), expense.getPayer(), share.getShareAmount());
            }
        }
        for (var payment : settlements.findByTrip_TripIdOrderBySettledAtDescSettlementIdDesc(tripId)) {
            // A payment reverses an obligation: Jack pays Amy 20, so Jack's debt falls by 20.
            add(pairs, names, userId, payment.getPayer(), payment.getPayee(), payment.getAmount().negate());
        }
        List<Obligation> owe = new ArrayList<>();
        List<Obligation> owed = new ArrayList<>();
        pairs.forEach((id, amount) -> {
            if (amount.signum() > 0) owe.add(new Obligation(id, names.get(id), amount));
            if (amount.signum() < 0) owed.add(new Obligation(id, names.get(id), amount.negate()));
        });
        BigDecimal totalOwe = owe.stream().map(Obligation::getAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal totalOwed = owed.stream().map(Obligation::getAmount).reduce(ZERO, BigDecimal::add);
        return new BalanceResponse(totalOwed.subtract(totalOwe), totalOwe, totalOwed, owe, owed);
    }

    public SimplifiedDebtResponse simplify(Long tripId, Long userId) {
        String currency = members.verifyUserIsMember(tripId, userId).getTrip().getBaseCurrency();
        Map<Long, BigDecimal> nets = new TreeMap<>();
        Map<Long, String> names = new HashMap<>();
        Map<Pair, BigDecimal> pairs = new HashMap<>();
        for (var expense : expenses.findByTrip_TripIdOrderByDateDescExpenseIdDesc(tripId)) {
            for (var share : expense.getShares()) {
                if (!share.getUser().getUserId().equals(expense.getPayer().getUserId()))
                    addNet(nets, names, pairs, share.getUser(), expense.getPayer(), share.getShareAmount());
            }
        }
        for (var payment : settlements.findByTrip_TripIdOrderBySettledAtDescSettlementIdDesc(tripId))
            addNet(nets, names, pairs, payment.getPayer(), payment.getPayee(), payment.getAmount().negate());
        Comparator<Position> order = Comparator.comparing(Position::amount).reversed().thenComparing(Position::userId);
        Queue<Position> debtors = new PriorityQueue<>(order);
        Queue<Position> creditors = new PriorityQueue<>(order);
        nets.forEach((id, net) -> {
            if (net.signum() < 0) debtors.add(new Position(id, net.negate()));
            if (net.signum() > 0) creditors.add(new Position(id, net));
        });
        List<SimplifiedDebtResponse.Transfer> transfers = new ArrayList<>();
        // Greedy matching is deterministic and preserves net positions; it does not claim a globally minimal plan.
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            Position debtor = debtors.remove(), creditor = creditors.remove();
            BigDecimal amount = debtor.amount().min(creditor.amount());
            transfers.add(new SimplifiedDebtResponse.Transfer(debtor.userId(), names.get(debtor.userId()),
                    creditor.userId(), names.get(creditor.userId()), amount));
            if (debtor.amount().compareTo(amount) > 0) debtors.add(new Position(debtor.userId(), debtor.amount().subtract(amount)));
            if (creditor.amount().compareTo(amount) > 0) creditors.add(new Position(creditor.userId(), creditor.amount().subtract(amount)));
        }
        if (!debtors.isEmpty() || !creditors.isEmpty()) throw new IllegalStateException("Trip net balances are inconsistent");
        return new SimplifiedDebtResponse(currency, pairs.values().stream().filter(a -> a.signum() != 0).count(), transfers);
    }
    private record Position(Long userId, BigDecimal amount) {}
    private record Pair(Long first, Long second) {}
    private void addNet(Map<Long, BigDecimal> nets, Map<Long, String> names, Map<Pair, BigDecimal> pairs,
                        User debtor, User creditor, BigDecimal amount) {
        Long from = debtor.getUserId(), to = creditor.getUserId();
        nets.merge(from, amount.negate(), BigDecimal::add);
        nets.merge(to, amount, BigDecimal::add);
        names.put(from, debtor.getUsername()); names.put(to, creditor.getUsername());
        boolean ascending = from < to;
        pairs.merge(new Pair(ascending ? from : to, ascending ? to : from), ascending ? amount : amount.negate(), BigDecimal::add);
    }

    private void add(Map<Long, BigDecimal> pairs, Map<Long, String> names, Long current,
                     User debtor, User creditor, BigDecimal amount) {
        // Only direct pairs involving this user; never transfer debt via a third member.
        if (debtor.getUserId().equals(current)) {
            pairs.merge(creditor.getUserId(), amount, BigDecimal::add);
            names.put(creditor.getUserId(), creditor.getUsername());
        } else if (creditor.getUserId().equals(current)) {
            pairs.merge(debtor.getUserId(), amount.negate(), BigDecimal::add);
            names.put(debtor.getUserId(), debtor.getUsername());
        }
    }
}
