package com.elec5619.group3.trip_planner_backend;

import com.elec5619.group3.trip_planner_backend.dto.*;
import com.elec5619.group3.trip_planner_backend.entity.*;
import com.elec5619.group3.trip_planner_backend.repository.*;
import com.elec5619.group3.trip_planner_backend.service.*;
import com.elec5619.group3.trip_planner_backend.filter.AuthenticatedUser;
import com.elec5619.group3.trip_planner_backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementIntegrationTests {
    @Autowired BalanceService balances;
    @Autowired SettlementService payments;
    @Autowired ExpenseService expenses;
    @Autowired TripService tripService;
    @Autowired SettlementRepository settlementRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired TripMemberRepository memberships;
    @Autowired TripRepository trips;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    User amy, jack, mia, outsider;
    Trip trip, otherTrip;

    @ParameterizedTest @ValueSource(ints = {3, 4, 7})
    void simplifiedCyclesCancelWithoutWrites(int size) {
        var people = new java.util.ArrayList<>(List.of(amy, jack, mia));
        for (int i = 3; i < size; i++) { User u = user("Cycle" + i); join(trip, u, "MEMBER"); people.add(u); }
        for (int i = 0; i < size; i++) debt(people.get(i), people.get((i + 1) % size), "10.01");
        var before = jdbc.queryForList("select * from expense_shares order by expense_share_id");
        var plan = balances.simplify(trip.getTripId(), amy.getUserId());
        assertEquals(size, plan.originalPairwiseRelationships()); assertTrue(plan.transfers().isEmpty());
        assertEquals(before, jdbc.queryForList("select * from expense_shares order by expense_share_id"));
        assertEquals(size, expenseRepository.count()); assertEquals(0, settlementRepository.count());
    }
    @Test void simplifiedMixedDebtsIncludeSettlementsAndPreserveEveryNet() {
        User fourth = user("Fourth"), fifth = user("Fifth"), sixth = user("Sixth");
        for (User u : List.of(fourth, fifth, sixth)) join(trip, u, "MEMBER");
        debt(amy, jack, "30.01"); debt(amy, mia, "10.02"); debt(fourth, mia, "20.03");
        debt(fifth, sixth, "5.04"); debt(sixth, jack, "3.05");
        pay(amy, jack, "4.06");
        var before = jdbc.queryForList("select * from expenses order by expense_id");
        var paymentsBefore = jdbc.queryForList("select * from settlements order by settlement_id");
        var plan = balances.simplify(trip.getTripId(), amy.getUserId());
        assertEquals(plan, balances.simplify(trip.getTripId(), amy.getUserId()));
        assertEquals("AUD", plan.currency());
        for (User u : List.of(amy, jack, mia, fourth, fifth, sixth)) {
            BigDecimal net = BigDecimal.ZERO;
            for (var transfer : plan.transfers()) {
                assertTrue(transfer.amount().signum() > 0); assertEquals(2, transfer.amount().scale());
                assertNotEquals(transfer.fromUserId(), transfer.toUserId());
                if (transfer.fromUserId().equals(u.getUserId())) net = net.subtract(transfer.amount());
                if (transfer.toUserId().equals(u.getUserId())) net = net.add(transfer.amount());
            }
            assertEquals(0, net.compareTo(view(u).getNetBalance()));
        }
        assertTrue(plan.transfers().size() <= 5);
        assertEquals(before, jdbc.queryForList("select * from expenses order by expense_id"));
        assertEquals(paymentsBefore, jdbc.queryForList("select * from settlements order by settlement_id"));
    }
    @Test void simplifiedSettledEmptyAndUnauthorised() throws Exception {
        assertTrue(balances.simplify(trip.getTripId(), amy.getUserId()).transfers().isEmpty());
        debt(jack, amy, "10.01"); pay(jack, amy, "10.01");
        assertTrue(balances.simplify(trip.getTripId(), amy.getUserId()).transfers().isEmpty());
        mvc.perform(get("/api/trips/{id}/balances/simplified", trip.getTripId()).with(authentication(auth(outsider))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/trips/{id}/balances/simplified", trip.getTripId())).andExpect(status().isUnauthorized());
    }
    @BeforeEach void setup() {
        settlementRepository.deleteAll(); expenseRepository.deleteAll(); memberships.deleteAll(); trips.deleteAll(); users.deleteAll();
        amy = user("Amy"); jack = user("Jack"); mia = user("Mia"); outsider = user("Outsider");
        trip = trip("First"); otherTrip = trip("Second");
        join(trip, amy, "OWNER"); join(trip, jack, "MEMBER"); join(trip, mia, "MEMBER");
        join(otherTrip, outsider, "OWNER");
    }
    User user(String name) {
        User u = new User(); u.setUsername(name); u.setEmail(name + "@example.test"); u.setPasswordHash("test-only");
        return users.save(u);
    }
    Trip trip(String name) {
        Trip t = new Trip(); t.setName(name); t.setDestination("Test");
        t.setStartDate(LocalDate.of(2026, 9, 1)); t.setEndDate(LocalDate.of(2026, 9, 10)); t.setPlannedBudget(new BigDecimal("100.00"));
        return trips.save(t);
    }
    TripMember join(Trip t, User u, String role) {
        TripMember m = new TripMember(); m.setTrip(t); m.setUser(u); m.setRole(role); return memberships.save(m);
    }
    void memberStatus(User u, String status) {
        TripMember m = memberships.findByTrip_TripIdAndUser_UserId(trip.getTripId(), u.getUserId()).orElseThrow();
        m.setInviteStatus(status); memberships.save(m);
    }
    ExpenseRequest request(User creditor, User debtor, String amount) {
        ExpenseRequest r = new ExpenseRequest(); r.setPayerId(creditor.getUserId()); r.setDescription("Test expense");
        r.setDate(LocalDate.of(2026, 9, 5)); r.setAmount(new BigDecimal(amount)); r.setSplitMode(ExpenseRequest.SplitMode.EQUAL);
        ExpenseRequest.Participant p = new ExpenseRequest.Participant(); p.setUserId(debtor.getUserId());
        r.setParticipants(List.of(p)); return r;
    }
    ExpenseResponse debt(User debtor, User creditor, String amount) {
        return expenses.create(trip.getTripId(), request(creditor, debtor, amount), amy.getUserId());
    }
    SettlementRequest payment(User payee, String amount) {
        SettlementRequest r = new SettlementRequest(); r.setPayeeId(payee.getUserId()); r.setAmount(new BigDecimal(amount)); return r;
    }
    SettlementResponse pay(User payer, User payee, String amount) {
        return payments.create(trip.getTripId(), payment(payee, amount), payer.getUserId());
    }
    BalanceResponse view(User u) { return balances.get(trip.getTripId(), u.getUserId()); }
    void owes(User debtor, User creditor, String amount) {
        BalanceResponse b = view(debtor);
        assertEquals(new BigDecimal(amount), b.getYouOwe().stream().filter(o -> o.getUserId().equals(creditor.getUserId()))
                .map(BalanceResponse.Obligation::getAmount).findFirst().orElse(new BigDecimal("0.00")));
        assertEquals(b.getTotalYouOwe(), expenses.summary(trip.getTripId(), debtor.getUserId()).getCurrentUserOwes());
    }
    UsernamePasswordAuthenticationToken auth(User u) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(u.getUserId(), u.getEmail()), null, List.of());
    }

    @Test void noExpensesMeansZero() {
        BalanceResponse b = view(jack);
        assertEquals(new BigDecimal("0.00"), b.getNetBalance());
        assertEquals(new BigDecimal("0.00"), b.getTotalYouOwe());
        assertEquals(new BigDecimal("0.00"), b.getTotalYouAreOwed());
        assertTrue(b.getYouOwe().isEmpty()); assertTrue(b.getYouAreOwed().isEmpty());
        assertTrue(payments.list(trip.getTripId(), jack.getUserId()).isEmpty());
    }
    @Test void payerOwnShareDoesNotCreateDebt() {
        ExpenseRequest r = request(amy, jack, "100.00"); r.setSplitMode(ExpenseRequest.SplitMode.CUSTOM);
        ExpenseRequest.Participant a = new ExpenseRequest.Participant(); a.setUserId(amy.getUserId()); a.setShareAmount(new BigDecimal("25.00"));
        ExpenseRequest.Participant j = r.getParticipants().get(0); j.setShareAmount(new BigDecimal("75.00")); r.setParticipants(List.of(a, j));
        expenses.create(trip.getTripId(), r, amy.getUserId());
        owes(jack, amy, "75.00"); assertTrue(view(amy).getYouOwe().isEmpty());
        assertEquals(jack.getUserId(), view(amy).getYouAreOwed().get(0).getUserId());
        assertEquals(new BigDecimal("75.00"), view(amy).getNetBalance());
        assertEquals(new BigDecimal("-75.00"), view(jack).getNetBalance());
    }
    @Test void selfOnlyExpenseIsBalanced() { debt(amy, amy, "100.00"); assertTrue(view(amy).getYouOwe().isEmpty()); assertTrue(view(amy).getYouAreOwed().isEmpty()); }
    @Test void multipleExpensesAggregate() { debt(jack, amy, "20.00"); debt(jack, amy, "30.00"); owes(jack, amy, "50.00"); }
    @Test void oppositeObligationsNetPairwise() {
        debt(jack, amy, "50.00"); debt(amy, jack, "20.00"); owes(jack, amy, "30.00");
        assertTrue(view(jack).getYouAreOwed().isEmpty()); assertTrue(view(amy).getYouOwe().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> pay(amy, jack, "1.00"));
        assertThrows(IllegalArgumentException.class, () -> pay(jack, amy, "31.00"));
        pay(jack, amy, "10.00"); owes(jack, amy, "20.00");
    }
    @Test void noTransitiveSimplificationEvenWithZeroNet() {
        debt(jack, amy, "20.00"); debt(amy, mia, "20.00");
        owes(jack, amy, "20.00"); owes(amy, mia, "20.00"); owes(jack, mia, "0.00");
        assertEquals(new BigDecimal("0.00"), view(amy).getNetBalance());
        assertEquals(new BigDecimal("20.00"), view(amy).getTotalYouOwe());
        assertEquals(new BigDecimal("20.00"), view(amy).getTotalYouAreOwed());
        assertThrows(IllegalArgumentException.class, () -> pay(jack, mia, "20.00"));
    }
    @Test void paymentReducesDebtAndSummaryWithoutChangingSpending() {
        debt(jack, amy, "50.00"); owes(jack, amy, "50.00");
        ExpenseSummaryResponse before = expenses.summary(trip.getTripId(), jack.getUserId());
        pay(jack, amy, "20.00"); owes(jack, amy, "30.00");
        assertEquals(new BigDecimal("30.00"), view(amy).getTotalYouAreOwed());
        ExpenseSummaryResponse after = expenses.summary(trip.getTripId(), jack.getUserId());
        assertEquals(before.getTotalSpending(), after.getTotalSpending()); assertEquals(before.getBudget(), after.getBudget());
        assertEquals(before.getRemaining(), after.getRemaining()); assertEquals(before.getBudgetUsedPercentage(), after.getBudgetUsedPercentage());
    }
    @Test void fullPaymentRemovesPair() {
        debt(jack, amy, "50.00"); pay(jack, amy, "50.00"); owes(jack, amy, "0.00");
        assertTrue(view(jack).getYouOwe().isEmpty()); assertTrue(view(amy).getYouAreOwed().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> pay(jack, amy, "0.01"));
    }
    @Test void partialPaymentsAggregate() {
        debt(jack, amy, "50.00"); pay(jack, amy, "10.00"); pay(jack, amy, "15.00"); owes(jack, amy, "25.00");
        assertEquals(2, payments.list(trip.getTripId(), jack.getUserId()).size());
    }
    @Test void unrelatedOppositePairPaymentsDoNotAffectDebt() {
        join(trip, outsider, "MEMBER"); debt(jack, amy, "50.00"); debt(mia, outsider, "25.00"); pay(mia, outsider, "10.00");
        debt(outsider, mia, "30.00"); pay(outsider, mia, "5.00"); owes(jack, amy, "50.00"); owes(outsider, mia, "10.00");
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.001", "100000000"})
    void invalidPaymentAmountsRejected(String amount) {
        debt(jack, amy, "50.00"); assertThrows(IllegalArgumentException.class, () -> pay(jack, amy, amount));
        assertEquals(0, settlementRepository.count()); owes(jack, amy, "50.00");
    }
    @Test void requiredFieldsRejectedAtServiceBoundary() {
        SettlementRequest r = new SettlementRequest();
        assertThrows(IllegalArgumentException.class, () -> payments.create(trip.getTripId(), r, jack.getUserId()));
    }
    @Test void selfPaymentRejected() { assertThrows(IllegalArgumentException.class, () -> pay(jack, jack, "1.00")); }
    @Test void payeeOutsideTripRejected() { assertThrows(AccessDeniedException.class, () -> pay(jack, outsider, "1.00")); }
    @Test void missingPayeeRejected() {
        SettlementRequest r = payment(amy, "1.00"); r.setPayeeId(Long.MAX_VALUE);
        assertThrows(AccessDeniedException.class, () -> payments.create(trip.getTripId(), r, jack.getUserId()));
    }
    @Test void missingTripRejected() {
        assertThrows(ResourceNotFoundException.class, () -> payments.create(Long.MAX_VALUE, payment(amy, "1.00"), jack.getUserId()));
        assertThrows(ResourceNotFoundException.class, () -> balances.get(Long.MAX_VALUE, jack.getUserId()));
    }
    @ParameterizedTest @ValueSource(strings = {"INVITED", "PENDING", "DECLINED"})
    void inactivePayerCannotReadOrPay(String state) {
        debt(jack, amy, "50.00"); memberStatus(jack, state);
        assertThrows(AccessDeniedException.class, () -> view(jack));
        assertThrows(AccessDeniedException.class, () -> pay(jack, amy, "1.00"));
        assertThrows(AccessDeniedException.class, () -> payments.list(trip.getTripId(), jack.getUserId()));
    }
    @ParameterizedTest @ValueSource(strings = {"INVITED", "PENDING", "DECLINED"})
    void inactivePayeeCannotReceivePayment(String state) {
        debt(jack, amy, "50.00"); memberStatus(amy, state);
        assertThrows(AccessDeniedException.class, () -> pay(jack, amy, "1.00")); assertEquals(0, settlementRepository.count());
    }
    @Test void nonMemberCannotReadOrPay() {
        assertThrows(AccessDeniedException.class, () -> view(outsider));
        assertThrows(AccessDeniedException.class, () -> pay(outsider, amy, "1.00"));
        assertThrows(AccessDeniedException.class, () -> payments.list(trip.getTripId(), outsider.getUserId()));
    }
    @Test void overpaymentRejectedWithoutPersistence() {
        debt(jack, amy, "50.00"); pay(jack, amy, "20.00");
        assertThrows(IllegalArgumentException.class, () -> pay(jack, amy, "30.01"));
        assertEquals(1, settlementRepository.count()); owes(jack, amy, "30.00");
    }
    @Test void cannotPayMemberWithoutDebt() { assertThrows(IllegalArgumentException.class, () -> pay(jack, amy, "1.00")); }
    @Test void historyNewestFirstAndTripScoped() {
        debt(jack, amy, "50.00"); SettlementResponse first = pay(jack, amy, "10.00"); SettlementResponse second = pay(jack, amy, "15.00");
        assertEquals(List.of(second.getSettlementId(), first.getSettlementId()), payments.list(trip.getTripId(), mia.getUserId()).stream().map(SettlementResponse::getSettlementId).toList());
        join(otherTrip, jack, "MEMBER"); join(otherTrip, amy, "MEMBER");
        assertTrue(payments.list(otherTrip.getTripId(), jack.getUserId()).isEmpty());
        assertEquals(new BigDecimal("0.00"), balances.get(otherTrip.getTripId(), jack.getUserId()).getTotalYouOwe());
        assertThrows(IllegalArgumentException.class, () -> payments.create(otherTrip.getTripId(), payment(amy, "1.00"), jack.getUserId()));
    }
    @Test void centsStayExactAcrossRepeatedPayments() {
        debt(jack, amy, "0.10"); debt(jack, amy, "0.20");
        for (int i = 0; i < 29; i++) pay(jack, amy, "0.01");
        owes(jack, amy, "0.01"); pay(jack, amy, "0.01"); owes(jack, amy, "0.00");
    }
    @Test void concurrentPaymentsCannotOverpay() throws Exception {
        debt(jack, amy, "50.00"); ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> { start.await(); try { pay(jack, amy, "30.00"); return true; } catch (IllegalArgumentException e) { return false; } };
        try {
            Future<Boolean> a = pool.submit(attempt); Future<Boolean> b = pool.submit(attempt); start.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(1, settlementRepository.count()); owes(jack, amy, "20.00");
        } finally { pool.shutdownNow(); }
    }
    @Test void expenseEditsAndDeletionRetainActualPayments() {
        ExpenseResponse e = debt(jack, amy, "50.00"); pay(jack, amy, "20.00");
        expenses.update(trip.getTripId(), e.getExpenseId(), request(amy, jack, "40.00"), amy.getUserId()); owes(jack, amy, "20.00");
        expenses.delete(trip.getTripId(), e.getExpenseId(), amy.getUserId());
        // The actual 20 payment remains; deleting the expense now leaves Amy owing Jack a refund.
        owes(amy, jack, "20.00"); assertEquals(1, payments.list(trip.getTripId(), jack.getUserId()).size());
    }
    @Test void tripDeletionCleansOnlyItsSettlements() {
        debt(jack, amy, "50.00"); pay(jack, amy, "20.00"); tripService.deleteTrip(trip.getTripId(), amy.getUserId());
        assertEquals(0, settlementRepository.count()); assertTrue(trips.existsById(otherTrip.getTripId()));
    }
    @Test void apiDerivesPayerFromPrincipalDespiteForgedField() throws Exception {
        debt(jack, amy, "50.00");
        mvc.perform(post("/api/trips/{id}/settlements", trip.getTripId()).with(authentication(auth(jack))).contentType("application/json")
                .content("{\"payerId\":%d,\"payeeId\":%d,\"amount\":20.00}".formatted(mia.getUserId(), amy.getUserId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.payerId").value(jack.getUserId()))
                .andExpect(jsonPath("$.payeeId").value(amy.getUserId())).andExpect(jsonPath("$.amount").value(20.00))
                .andExpect(jsonPath("$.settledAt").exists()).andExpect(jsonPath("$.settlementId").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.payer").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.token").doesNotExist());
        owes(jack, amy, "30.00");
    }
    @Test void ownerCannotForgePaymentOnBehalfOfAnotherMember() throws Exception {
        debt(jack, mia, "50.00");
        mvc.perform(post("/api/trips/{id}/settlements", trip.getTripId()).with(authentication(auth(amy))).contentType("application/json")
                .content("{\"payerId\":%d,\"payeeId\":%d,\"amount\":20.00}".formatted(jack.getUserId(), mia.getUserId())))
                .andExpect(status().isBadRequest()); assertEquals(0, settlementRepository.count()); owes(jack, mia, "50.00");
    }
    @Test void apiBalancesAndSummaryAgreeAfterPayment() throws Exception {
        debt(jack, amy, "50.00"); pay(jack, amy, "20.00");
        mvc.perform(get("/api/trips/{id}/balances", trip.getTripId()).param("userId", amy.getUserId().toString()).with(authentication(auth(jack))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalYouOwe").value(30)).andExpect(jsonPath("$.netBalance").value(-30))
                .andExpect(jsonPath("$.youOwe[0].userId").value(amy.getUserId())).andExpect(jsonPath("$.youAreOwed").isEmpty());
        mvc.perform(get("/api/trips/{id}/expenses/summary", trip.getTripId()).with(authentication(auth(jack))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentUserOwes").value(30));
        mvc.perform(get("/api/trips/{id}/settlements", trip.getTripId()).with(authentication(auth(jack))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].payerName").value("Jack")).andExpect(jsonPath("$[0].payeeName").value("Amy"));
    }
    @Test void apiRejectsMissingFieldsAndOverpayment() throws Exception {
        for (String json : List.of("{}", "{\"payeeId\":1}", "{\"amount\":1}", "{\"payeeId\":1,\"amount\":0}"))
            mvc.perform(post("/api/trips/{id}/settlements", trip.getTripId()).with(authentication(auth(jack))).contentType("application/json").content(json))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        debt(jack, amy, "50.00");
        mvc.perform(post("/api/trips/{id}/settlements", trip.getTripId()).with(authentication(auth(jack))).contentType("application/json")
                .content("{\"payeeId\":%d,\"amount\":50.01}".formatted(amy.getUserId())))
                .andExpect(status().isBadRequest()); assertEquals(0, settlementRepository.count());
    }
    @Test void apiRequiresAuthenticationAndActiveTripMembership() throws Exception {
        for (String route : List.of("balances", "settlements")) {
            mvc.perform(get("/api/trips/{id}/" + route, trip.getTripId())).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/trips/{id}/" + route, trip.getTripId()).with(authentication(auth(outsider)))).andExpect(status().isForbidden());
            mvc.perform(get("/api/trips/{id}/" + route, otherTrip.getTripId()).with(authentication(auth(jack)))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/trips/{id}/settlements", trip.getTripId()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/trips/{id}/settlements", otherTrip.getTripId()).with(authentication(auth(jack))).contentType("application/json")
                .content("{\"payeeId\":%d,\"amount\":1}".formatted(outsider.getUserId())))
                .andExpect(status().isForbidden());
        memberStatus(jack, "PENDING");
        mvc.perform(get("/api/trips/{id}/balances", trip.getTripId()).with(authentication(auth(jack)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/trips/{id}/settlements", trip.getTripId()).with(authentication(auth(jack)))).andExpect(status().isForbidden());
    }
}
