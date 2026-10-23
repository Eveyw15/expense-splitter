package com.elec5619.group3.trip_planner_backend.service;

import com.elec5619.group3.trip_planner_backend.dto.*;
import com.elec5619.group3.trip_planner_backend.entity.*;
import com.elec5619.group3.trip_planner_backend.exception.ResourceNotFoundException;
import com.elec5619.group3.trip_planner_backend.repository.ExpenseRepository;
import com.elec5619.group3.trip_planner_backend.repository.TripRepository;
import com.elec5619.group3.trip_planner_backend.repository.TripMemberRepository;
import jakarta.validation.Validator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ExpenseService {
    private final ExpenseRepository expenses;
    private final TripMemberService members;
    private final Validator validator;
    private final TripMemberRepository memberships;
    private final BalanceService balances;
    private final TripRepository trips;
    private final CurrencyService currencies;

    public ExpenseService(ExpenseRepository expenses, TripMemberService members, Validator validator, TripMemberRepository memberships, BalanceService balances, TripRepository trips, CurrencyService currencies) {
       
        this.currencies = currencies;
        this.balances = balances;
        this.trips = trips;
        this.memberships = memberships;
        this.expenses = expenses;
        this.members = members;
        this.validator = validator;
    }

    public List<ExpenseResponse> list(Long tripId, Long userId) {
        TripMember caller = members.verifyUserIsMember(tripId, userId);
        return expenses.findByTrip_TripIdOrderByDateDescExpenseIdDesc(tripId).stream()
                .map(expense -> response(expense, caller)).toList();
    }

    public ExpenseResponse get(Long tripId, Long expenseId, Long userId) {
        TripMember caller = members.verifyUserIsMember(tripId, userId);
        return response(find(tripId, expenseId), caller);
    }

    @Transactional
    public ExpenseResponse create(Long tripId, ExpenseRequest request, Long userId) {
        trips.lockFinancialWrites(tripId);
        TripMember caller = members.verifyUserIsMember(tripId, userId);
        Expense expense = new Expense();
        expense.setTrip(caller.getTrip());
        expense.setCreatedBy(caller.getUser());
        apply(expense, request);
        return response(expenses.saveAndFlush(expense), caller);
    }

    @Transactional
    public ExpenseResponse update(Long tripId, Long expenseId, ExpenseRequest request, Long userId) {
        trips.lockFinancialWrites(tripId);
        TripMember caller = members.verifyUserIsMember(tripId, userId);
        Expense expense = find(tripId, expenseId);
        requireEditor(expense, caller);
        apply(expense, request);
        return response(expenses.saveAndFlush(expense), caller);
    }

    @Transactional
    public void delete(Long tripId, Long expenseId, Long userId) {
        trips.lockFinancialWrites(tripId);
        TripMember caller = members.verifyUserIsMember(tripId, userId);
        Expense expense = find(tripId, expenseId);
        requireEditor(expense, caller);
        expenses.delete(expense);
    }

    public record ReceiptContent(String fileName, String contentType, byte[] bytes) {}

    public ReceiptContent receipt(Long tripId, Long expenseId, Long userId) {
        members.verifyUserIsMember(tripId, userId);
        ExpenseReceipt receipt = find(tripId, expenseId).getReceipt();
        if (receipt == null) throw new ResourceNotFoundException("Receipt not found");
        return new ReceiptContent(receipt.getFileName(), receipt.getContentType(), receipt.getContent());
    }

    @Transactional
    public ExpenseResponse saveReceipt(Long tripId, Long expenseId, MultipartFile file, Long userId) {
        TripMember caller = members.verifyUserIsMember(tripId, userId);
        Expense expense = find(tripId, expenseId);
        requireEditor(expense, caller);
        if (file == null || file.isEmpty() || file.getSize() > 5_000_000) throw new IllegalArgumentException("Receipt must be 1 to 5 MB");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (java.io.IOException e) { throw new IllegalArgumentException("Unable to read receipt", e); }
        String type;
        if (bytes.length >= 3 && (bytes[0] & 255) == 0xff && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff) type = "image/jpeg";
        else if (bytes.length >= 8 && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) type = "image/png";
        else if (bytes.length >= 5 && new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) type = "application/pdf";
        else throw new IllegalArgumentException("Only JPEG, PNG and PDF receipts are supported");
        String name = file.getOriginalFilename() == null ? "receipt" : file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 255) throw new IllegalArgumentException("Invalid receipt filename");
        ExpenseReceipt receipt = expense.getReceipt();
        if (receipt == null) { receipt = new ExpenseReceipt(); receipt.setExpense(expense); expense.setReceipt(receipt); }
        receipt.setFileName(name); receipt.setContentType(type); receipt.setContent(bytes);
        receipt.setUploadedBy(caller.getUser()); receipt.setUploadedAt(LocalDateTime.now());
        return response(expenses.saveAndFlush(expense), caller);
    }

    @Transactional
    public void removeReceipt(Long tripId, Long expenseId, Long userId) {
        TripMember caller = members.verifyUserIsMember(tripId, userId);
        Expense expense = find(tripId, expenseId);
        requireEditor(expense, caller);
        if (expense.getReceipt() != null) { expense.setReceipt(null); expenses.saveAndFlush(expense); }
    }

    public ExpenseSummaryResponse summary(Long tripId, Long userId) {
        TripMember caller = members.verifyUserIsMember(tripId, userId);
        List<Expense> records = expenses.findByTrip_TripIdOrderByDateDescExpenseIdDesc(tripId);
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Expense expense : records) {
            total = total.add(expense.getAmount());
        }
        BigDecimal budget = caller.getTrip().getPlannedBudget();
        if (budget == null) budget = BigDecimal.ZERO.setScale(2);
        BigDecimal percent = budget.signum() > 0
                ? total.multiply(new BigDecimal("100")).divide(budget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        return new ExpenseSummaryResponse(total, budget, budget.subtract(total),
                balances.calculate(tripId, userId, records).getTotalYouOwe(), percent);
    }

    public BudgetResponse budget(Long tripId, Long userId) {
        TripMember caller = members.verifyUserIsMember(tripId, userId);
        return new BudgetResponse(caller.getTrip().getPlannedBudget(), isOwner(caller));
    }

    @Transactional
    public BudgetResponse updateBudget(Long tripId, BudgetRequest request, Long userId) {
        TripMember caller = members.verifyUserIsMember(tripId, userId);
        if (!isOwner(caller)) throw new AccessDeniedException("Only trip owners can update the budget");
        validate(request);
        caller.getTrip().setPlannedBudget(request.getPlannedBudget().setScale(2));
        return new BudgetResponse(caller.getTrip().getPlannedBudget(), true);
    }

    private Expense find(Long tripId, Long expenseId) {
        return expenses.findByExpenseIdAndTrip_TripId(expenseId, tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
    }

    private void validate(Object request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
    }

    private void apply(Expense expense, ExpenseRequest request) {
        validate(request);
        Map<Long, User> activeMembers = memberships.findByTrip_TripId(expense.getTrip().getTripId()).stream()
                .filter(TripMember::isActive).map(TripMember::getUser)
                .collect(Collectors.toMap(User::getUserId, Function.identity()));
        User payer = activeMembers.get(request.getPayerId());
        if (payer == null) throw new AccessDeniedException("Payer must be an active member of this trip");
        // Validate all shares before touching the managed expense or its existing shares.
        Map<Long, BigDecimal> allocations = request.getSplitMode() == ExpenseRequest.SplitMode.BY_ITEM
                ? itemAllocations(activeMembers, request) : allocations(activeMembers, request);
        String currency = CurrencyService.validateCode(request.getCurrencyCode() == null ? expense.getTrip().getBaseCurrency() : request.getCurrencyCode());
        boolean financialChange = expense.getExpenseId() == null || request.getAmount().compareTo(expense.getOriginalAmount()) != 0
                || !currency.equals(expense.getCurrencyCode()) || !request.getDate().equals(expense.getDate());
        CurrencyService.Quote quote = financialChange
                ? currencies.getRate(currency, expense.getTrip().getBaseCurrency(), request.getDate())
                : new CurrencyService.Quote(expense.getExchangeRateToBase(), expense.getExchangeRateDate());
        BigDecimal baseAmount = financialChange ? CurrencyService.baseAmount(request.getAmount(), quote.rate()) : expense.getAmount();
        if (baseAmount.signum() <= 0) throw new IllegalArgumentException("Converted expense must be at least 0.01 in the trip base currency");
        Map<Long, BigDecimal> baseAllocations = baseAllocations(allocations, baseAmount, request.getAmount(), request.getSplitMode());
        Map<Long, ExpenseShare> existing = expense.getShares().stream()
                .collect(Collectors.toMap(s -> s.getUser().getUserId(), Function.identity()));
        boolean sameSplit = !financialChange && existing.size() == allocations.size() && allocations.entrySet().stream()
                .allMatch(a -> existing.containsKey(a.getKey()) && existing.get(a.getKey()).getOriginalShareAmount().compareTo(a.getValue()) == 0);
        expense.getShares().removeIf(s -> !allocations.containsKey(s.getUser().getUserId()));
        for (var allocation : allocations.entrySet()) {
            ExpenseShare share = existing.get(allocation.getKey());
            if (share == null) {
                share = new ExpenseShare();
                share.setExpense(expense);
                share.setUser(activeMembers.get(allocation.getKey()));
                expense.getShares().add(share);
            }
            if (!sameSplit) share.setShareAmount(baseAllocations.get(allocation.getKey()));
            share.setOriginalShareAmount(allocation.getValue());
        }
        expense.setPayer(payer);
        expense.setDescription(request.getDescription().trim());
        expense.setAmount(baseAmount);
        expense.setOriginalAmount(request.getAmount().setScale(2));
        expense.setCurrencyCode(currency);
        expense.setExchangeRateToBase(quote.rate());
        expense.setExchangeRateDate(quote.date());
        expense.setDate(request.getDate());
        expense.setCategory(request.getCategory());
        if (request.getSplitMode() == ExpenseRequest.SplitMode.BY_ITEM && !sameItems(expense.getItems(), request.getItems())) {
            // Replace only after validation and currency conversion have succeeded.
            expense.getItems().clear();
            int position = 0;
            for (ExpenseRequest.Item input : request.getItems()) {
                ExpenseItem item = new ExpenseItem();
                item.setExpense(expense); item.setName(input.getName().trim());
                item.setAmount(input.getAmount().setScale(2)); item.setQuantity(input.getQuantity());
                item.setSortOrder(position++);
                long cents = itemTotal(input).movePointRight(2).longValueExact();
                var ids = input.getUserIds().stream().sorted().toList();
                for (int i = 0; i < ids.size(); i++) {
                    ExpenseItemAllocation allocation = new ExpenseItemAllocation();
                    allocation.setItem(item); allocation.setUser(activeMembers.get(ids.get(i)));
                    allocation.setShareAmount(BigDecimal.valueOf(cents / ids.size() + (i < cents % ids.size() ? 1 : 0), 2));
                    item.getAllocations().add(allocation);
                }
                expense.getItems().add(item);
            }
        } else if (request.getSplitMode() != ExpenseRequest.SplitMode.BY_ITEM) expense.getItems().clear();
        expense.setSplitMode(request.getSplitMode().name());
    }

    private boolean sameItems(List<ExpenseItem> saved, List<ExpenseRequest.Item> requested) {
        if (saved.size() != requested.size()) return false;
        for (int i = 0; i < saved.size(); i++) {
            ExpenseItem left = saved.get(i); ExpenseRequest.Item right = requested.get(i);
            var ids = right.getUserIds().stream().sorted().toList();
            if (!left.getName().equals(right.getName().trim()) || !java.util.Objects.equals(left.getQuantity(), right.getQuantity())
                    || left.getAmount().compareTo(right.getAmount()) != 0
                    || !left.getAllocations().stream().map(a -> a.getUser().getUserId()).sorted().toList()
                            .equals(ids)) return false;
            long cents = itemTotal(right).movePointRight(2).longValueExact();
            for (int position = 0; position < ids.size(); position++) {
                long userId = ids.get(position);
                BigDecimal expected = BigDecimal.valueOf(cents / ids.size() + (position < cents % ids.size() ? 1 : 0), 2);
                if (left.getAllocations().stream().noneMatch(a -> a.getUser().getUserId().equals(userId)
                        && a.getShareAmount().compareTo(expected) == 0)) return false;
            }
        }
        return true;
    }

    private Map<Long, BigDecimal> itemAllocations(Map<Long, User> activeMembers, ExpenseRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) throw new IllegalArgumentException("Add at least one item");
        Map<Long, BigDecimal> result = new TreeMap<>();
        for (ExpenseRequest.Participant participant : request.getParticipants()) {
            Long id = participant.getUserId();
            if (result.putIfAbsent(id, BigDecimal.ZERO.setScale(2)) != null) throw new IllegalArgumentException("Duplicate split participant");
            if (!activeMembers.containsKey(id)) throw new AccessDeniedException("Split participants must be active members of this trip");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ExpenseRequest.Item item : request.getItems()) {
            if (item == null || item.getName() == null || item.getName().isBlank() || item.getName().length() > 200
                    || item.getAmount() == null || item.getAmount().signum() <= 0 || item.getAmount().scale() > 2
                    || item.getAmount().compareTo(new BigDecimal("99999999.99")) > 0
                    || item.getQuantity() != null && item.getQuantity() < 1
                    || item.getUserIds() == null || item.getUserIds().isEmpty())
                throw new IllegalArgumentException("Invalid item");
            var ids = item.getUserIds().stream().sorted().toList();
            if (ids.stream().distinct().count() != ids.size()) throw new IllegalArgumentException("Duplicate item assignment");
            if (!result.keySet().containsAll(ids)) throw new IllegalArgumentException("Items can only be assigned to selected participants");
            BigDecimal lineTotal = itemTotal(item);
            if (lineTotal.compareTo(new BigDecimal("99999999.99")) > 0)
                throw new IllegalArgumentException("Invalid item");
            long cents = lineTotal.movePointRight(2).longValueExact();
            for (int i = 0; i < ids.size(); i++) {
                Long id = ids.get(i);
                result.put(id, result.get(id).add(BigDecimal.valueOf(cents / ids.size() + (i < cents % ids.size() ? 1 : 0), 2)));
            }
            total = total.add(lineTotal);
        }
        if (total.compareTo(request.getAmount()) != 0) throw new IllegalArgumentException("Item amounts must total the expense amount");
        return result;
    }

    private BigDecimal itemTotal(ExpenseRequest.Item item) {
        return item.getAmount().multiply(BigDecimal.valueOf(item.getQuantity() == null ? 1 : item.getQuantity()));
    }

    private Map<Long, BigDecimal> allocations(Map<Long, User> activeMembers, ExpenseRequest request) {
        // TreeMap makes the remainder distribution stable by user ID, independent of request order.
        Map<Long, BigDecimal> result = new TreeMap<>();
        for (ExpenseRequest.Participant participant : request.getParticipants()) {
            Long id = participant.getUserId();
            if (result.containsKey(id)) throw new IllegalArgumentException("Duplicate split participant");
            if (!activeMembers.containsKey(id)) throw new AccessDeniedException("Split participants must be active members of this trip");
            BigDecimal amount = participant.getShareAmount();
            if (request.getSplitMode() == ExpenseRequest.SplitMode.CUSTOM && amount == null)
                throw new IllegalArgumentException("Custom shares require an amount for every participant");
            result.put(id, amount == null ? BigDecimal.ZERO.setScale(2) : amount.setScale(2));
        }
        if (request.getSplitMode() == ExpenseRequest.SplitMode.EQUAL) {
            long cents = request.getAmount().movePointRight(2).longValueExact();
            long base = cents / result.size();
            long remainder = cents % result.size();
            for (Long id : result.keySet()) {
                result.put(id, BigDecimal.valueOf(base + (remainder-- > 0 ? 1 : 0), 2));
            }
        } else {
            BigDecimal sum = result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(request.getAmount()) != 0)
                throw new IllegalArgumentException("Custom shares must total the expense amount exactly");
        }
        return result;
    }

    private Map<Long, BigDecimal> baseAllocations(Map<Long, BigDecimal> original, BigDecimal total,
                                                       BigDecimal originalTotal, ExpenseRequest.SplitMode mode) {
        Map<Long, BigDecimal> result = new TreeMap<>();
        Map<Long, BigDecimal> fractions = new TreeMap<>();
        long cents = total.movePointRight(2).longValueExact();
        long allocated = 0;
        for (var entry : original.entrySet()) {
            // Proportion the rounded base total; largest remainders receive leftover cents.
            BigDecimal exact = mode == ExpenseRequest.SplitMode.EQUAL
                    ? BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(original.size()), 20, RoundingMode.DOWN)
                    : entry.getValue().multiply(BigDecimal.valueOf(cents)).divide(originalTotal, 20, RoundingMode.DOWN);
            long whole = exact.longValue();
            result.put(entry.getKey(), BigDecimal.valueOf(whole, 2));
            fractions.put(entry.getKey(), exact.subtract(BigDecimal.valueOf(whole)));
            allocated += whole;
        }
        var order = original.keySet().stream().sorted(java.util.Comparator
                .<Long, BigDecimal>comparing(fractions::get).reversed().thenComparingLong(Long::longValue)).toList();
        for (int i = 0; i < cents - allocated; i++) result.compute(order.get(i), (id, value) -> value.add(new BigDecimal("0.01")));
        return result;
    }

    private BigDecimal userShare(Expense expense, Long userId) {
        return expense.getShares().stream().filter(s -> s.getUser().getUserId().equals(userId))
                .map(ExpenseShare::getShareAmount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private boolean isOwner(TripMember member) { return "OWNER".equals(member.getRole()); }
    private boolean canEdit(Expense expense, TripMember caller) {
        return isOwner(caller) || expense.getCreatedBy().getUserId().equals(caller.getUser().getUserId());
    }
    private void requireEditor(Expense expense, TripMember caller) {
        if (!canEdit(expense, caller)) throw new AccessDeniedException("Only the expense creator or trip owner can edit this expense");
    }

    private ExpenseResponse response(Expense expense, TripMember caller) {
        return ExpenseResponse.builder()
                .expenseId(expense.getExpenseId()).tripId(expense.getTrip().getTripId())
                .payerId(expense.getPayer().getUserId()).payerName(expense.getPayer().getUsername())
                .createdById(expense.getCreatedBy().getUserId()).createdByName(expense.getCreatedBy().getUsername())
                .description(expense.getDescription()).amount(expense.getAmount())
                .originalAmount(expense.getOriginalAmount()).currencyCode(expense.getCurrencyCode()).baseCurrency(expense.getTrip().getBaseCurrency())
                .exchangeRateToBase(expense.getExchangeRateToBase()).exchangeRateDate(expense.getExchangeRateDate())
                .date(expense.getDate()).category(expense.getCategory())
                .createdAt(expense.getCreatedAt()).updatedAt(expense.getUpdatedAt())
                .currentUserShare(userShare(expense, caller.getUser().getUserId()))
                .shares(expense.getShares().stream().map(s -> new ExpenseResponse.Share(
                        s.getUser().getUserId(), s.getUser().getUsername(), s.getShareAmount(), s.getOriginalShareAmount())).toList())
                .splitMode(expense.getSplitMode() == null ? "CUSTOM" : expense.getSplitMode())
                .items(expense.getItems().stream().map(item -> new ExpenseResponse.Item(item.getId(), item.getName(), item.getQuantity(), item.getAmount(),
                        item.getAllocations().stream().sorted(java.util.Comparator.comparing(a -> a.getUser().getUserId()))
                                .map(a -> new ExpenseResponse.Allocation(a.getUser().getUserId(), a.getUser().getUsername(), a.getShareAmount())).toList())).toList())
                .receipt(expense.getReceipt() == null ? null : new ExpenseResponse.Receipt(expense.getReceipt().getFileName(),
                        expense.getReceipt().getContentType(), expense.getReceipt().getUploadedBy().getUsername(), expense.getReceipt().getUploadedAt()))
                .canEdit(canEdit(expense, caller)).build();
    }
}
