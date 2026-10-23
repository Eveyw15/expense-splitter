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
import org.springframework.mock.web.MockMultipartFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExpenseIntegrationTests {
    @org.springframework.boot.test.context.TestConfiguration
    static class CurrencyTestConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        TestCurrencyService testCurrencies() { return new TestCurrencyService(); }
    }
    static class TestCurrencyService extends CurrencyService {
        BigDecimal rate = new BigDecimal("1.49");
        boolean fail;
        @Override public Quote getRate(String from, String to, LocalDate date) {
            validateCode(from); validateCode(to);
            if (from.equals(to)) return new Quote(BigDecimal.ONE, date == null ? LocalDate.now() : date);
            if (fail) throw new IllegalStateException("Exchange rate unavailable");
            return new Quote(rate, date == null ? LocalDate.now() : date);
        }
    }
    @Autowired TestCurrencyService currencies;
    @Autowired PersonalBudgetService personalBudgetService;
    @Autowired PersonalBudgetRepository personalBudgets;
    @Autowired SettlementService settlementService;
    @org.junit.jupiter.api.AfterEach void cleanPersonalBudgets() { personalBudgets.deleteAll(); }
    @Autowired ExpenseService service;
    @Autowired TripService tripService;
    @Autowired TripMemberService memberService;
    @Autowired ExpenseRepository expenses;
    @Autowired SettlementRepository settlements;
    @Autowired TripMemberRepository memberships;
    @Autowired TripRepository trips;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    User owner, creator, payer, outsider;
    Trip trip, otherTrip;

    @BeforeEach
    void setup() {
        currencies.fail = false; currencies.rate = new BigDecimal("1.49");
        personalBudgets.deleteAll(); settlements.deleteAll(); expenses.deleteAll(); memberships.deleteAll(); trips.deleteAll(); users.deleteAll();
        owner = user("owner"); creator = user("creator"); payer = user("payer"); outsider = user("outsider");
        trip = trip("First trip"); otherTrip = trip("Second trip");
        join(trip, owner, "OWNER"); join(trip, creator, "MEMBER"); join(trip, payer, "MEMBER");
        join(otherTrip, outsider, "OWNER");
    }
    User user(String name) {
        User user = new User(); user.setUsername(name); user.setEmail(name + "@example.test"); user.setPasswordHash("test-only");
        return users.save(user);
    }
    Trip trip(String name) {
        Trip t = new Trip(); t.setName(name); t.setDestination("Test destination");
        t.setStartDate(LocalDate.of(2026, 9, 1)); t.setEndDate(LocalDate.of(2026, 9, 10)); t.setPlannedBudget(new BigDecimal("100.00"));
        return trips.save(t);
    }
    TripMember join(Trip t, User u, String role) {
        TripMember m = new TripMember(); m.setTrip(t); m.setUser(u); m.setRole(role); return memberships.save(m);
    }
    ExpenseRequest request(String amount) {
        ExpenseRequest r = new ExpenseRequest(); r.setPayerId(payer.getUserId()); r.setDescription("Lunch");
        r.setSplitMode(ExpenseRequest.SplitMode.EQUAL); r.setParticipants(List.of(participant(creator, null)));
        r.setAmount(new BigDecimal(amount)); r.setDate(LocalDate.of(2026, 9, 5)); r.setCategory("Food"); return r;
    }
    ExpenseResponse createExpense() { return service.create(trip.getTripId(), request("24.50"), creator.getUserId()); }

    ExpenseRequest.Item item(String name, String amount, User... assigned) {
        var item = new ExpenseRequest.Item(); item.setName(name); item.setAmount(new BigDecimal(amount));
        item.setUserIds(java.util.Arrays.stream(assigned).map(User::getUserId).toList()); return item;
    }
    @Test void byItemQuantityMultipliesLineTotalAndAllocations() {
        ExpenseRequest request = request("74.00");
        request.setSplitMode(ExpenseRequest.SplitMode.BY_ITEM);
        request.setParticipants(List.of(participant(owner, null), participant(creator, null)));
        var npm = item("npm", "37.00", owner, creator);
        npm.setQuantity(2);
        request.setItems(List.of(npm));
        var saved = service.create(trip.getTripId(), request, creator.getUserId());
        assertEquals(new BigDecimal("74.00"), saved.getOriginalAmount());
        assertEquals(new BigDecimal("37.00"), saved.getItems().get(0).amount()); // Stored as unit price.
        assertEquals(List.of(new BigDecimal("37.00"), new BigDecimal("37.00")),
                saved.getItems().get(0).allocations().stream().map(ExpenseResponse.Allocation::shareAmount).toList());
        assertEquals(saved.getOriginalAmount(), saved.getShares().stream()
                .map(ExpenseResponse.Share::getOriginalShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("74.00"), service.summary(trip.getTripId(), creator.getUserId()).getTotalSpending());
        assertEquals(saved.getOriginalAmount(), service.get(trip.getTripId(), saved.getExpenseId(), owner.getUserId()).getOriginalAmount());
    }
    @Test void editingLegacyQuantityExpenseRepairsItemAllocations() {
        ExpenseRequest request = request("37.00");
        request.setSplitMode(ExpenseRequest.SplitMode.BY_ITEM);
        request.setParticipants(List.of(participant(owner, null), participant(creator, null)));
        var npm = item("npm", "37.00", owner, creator);
        request.setItems(List.of(npm));
        var saved = service.create(trip.getTripId(), request, creator.getUserId());
        jdbc.update("UPDATE expense_items SET quantity = 2 WHERE id = ?", saved.getItems().get(0).id());

        npm.setQuantity(2);
        request.setAmount(new BigDecimal("74.00"));
        var repaired = service.update(trip.getTripId(), saved.getExpenseId(), request, creator.getUserId());
        assertEquals(new BigDecimal("74.00"), repaired.getOriginalAmount());
        assertEquals(List.of(new BigDecimal("37.00"), new BigDecimal("37.00")),
                repaired.getItems().get(0).allocations().stream().map(ExpenseResponse.Allocation::shareAmount).toList());
    }
    @Test void byItemPersistsCentExactOriginalAndBaseSharesThroughEdits() {
        User fourth = user("fourth"); join(trip, fourth, "MEMBER");
        ExpenseRequest request = request("10.03"); request.setCurrencyCode("USD");
        request.setSplitMode(ExpenseRequest.SplitMode.BY_ITEM);
        request.setParticipants(List.of(participant(owner, null), participant(creator, null), participant(payer, null), participant(fourth, null)));
        request.setItems(List.of(item("Shared", "10.01", owner, creator, payer), item("Extra", "0.02", fourth)));
        var saved = service.create(trip.getTripId(), request, creator.getUserId());
        assertEquals(new BigDecimal("14.94"), saved.getAmount());
        assertEquals(new BigDecimal("10.03"), saved.getOriginalAmount());
        assertEquals(new BigDecimal("3.34"), saved.getItems().get(0).allocations().stream().filter(a -> a.userId().equals(owner.getUserId())).findFirst().orElseThrow().shareAmount());
        assertEquals(saved.getOriginalAmount(), saved.getItems().stream().flatMap(i -> i.allocations().stream()).map(ExpenseResponse.Allocation::shareAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(saved.getOriginalAmount(), saved.getShares().stream().map(ExpenseResponse.Share::getOriginalShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(saved.getAmount(), saved.getShares().stream().map(ExpenseResponse.Share::getShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        rate("2.00"); request.setDescription("Changed metadata");
        var edited = service.update(trip.getTripId(), saved.getExpenseId(), request, creator.getUserId());
        assertEquals(saved.getAmount(), edited.getAmount()); assertEquals(saved.getShares().stream().map(ExpenseResponse.Share::getShareAmount).toList(), edited.getShares().stream().map(ExpenseResponse.Share::getShareAmount).toList());
        request.setItems(List.of(item("Replacement", "10.03", creator, fourth)));
        edited = service.update(trip.getTripId(), saved.getExpenseId(), request, creator.getUserId());
        assertEquals(1, edited.getItems().size()); assertEquals("Replacement", service.get(trip.getTripId(), saved.getExpenseId(), owner.getUserId()).getItems().get(0).name());
        request.setItems(List.of(item("Replacement", "10.02", creator, fourth), item("Added", "0.01", payer)));
        assertEquals(2, service.update(trip.getTripId(), saved.getExpenseId(), request, creator.getUserId()).getItems().size());
        service.delete(trip.getTripId(), saved.getExpenseId(), creator.getUserId());
        assertEquals(0, jdbc.queryForObject("select count(*) from expense_items", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from expense_item_allocations", Integer.class));
    }
    @Test void byItemRejectsUnassignedDuplicateAndMismatchedItems() {
        ExpenseRequest request = request("1.00"); request.setSplitMode(ExpenseRequest.SplitMode.BY_ITEM);
        request.setItems(List.of(item("Wrong", "0.99", creator)));
        assertThrows(IllegalArgumentException.class, () -> service.create(trip.getTripId(), request, creator.getUserId()));
        request.setItems(List.of(item("Duplicate", "1.00", creator, creator)));
        assertThrows(IllegalArgumentException.class, () -> service.create(trip.getTripId(), request, creator.getUserId()));
        request.setItems(List.of(item("Other member", "1.00", payer)));
        assertThrows(IllegalArgumentException.class, () -> service.create(trip.getTripId(), request, creator.getUserId()));
        assertEquals(0, expenses.count());
    }
    @Test void receiptsValidateBytesAndEnforceTripAndEditorAccess() throws Exception {
        var saved = createExpense(); long id = saved.getExpenseId(); long tripId = trip.getTripId();
        var jpeg = new MockMultipartFile("file", "photo.jpg", "text/plain", new byte[]{(byte)255,(byte)216,(byte)255,1});
        mvc.perform(multipart("/api/trips/{tripId}/expenses/{id}/receipt", tripId, id).file(jpeg).with(authentication(auth(creator))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receipt.contentType").value("image/jpeg"));
        mvc.perform(get("/api/trips/{tripId}/expenses/{id}/receipt", tripId, id).with(authentication(auth(owner))))
                .andExpect(status().isOk()).andExpect(content().bytes(jpeg.getBytes()));
        mvc.perform(get("/api/trips/{tripId}/expenses/{id}/receipt", tripId, id).with(authentication(auth(outsider))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/trips/{tripId}/expenses/{id}/receipt", otherTrip.getTripId(), id).with(authentication(auth(outsider))))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/trips/{tripId}/expenses/{id}/receipt", tripId, id).file(jpeg).with(authentication(auth(payer))))
                .andExpect(status().isForbidden());
        var pdf = new MockMultipartFile("file", "bill.pdf", "application/pdf", "%PDF-1.7".getBytes());
        service.saveReceipt(tripId, id, pdf, creator.getUserId());
        assertEquals("bill.pdf", service.get(tripId, id, owner.getUserId()).getReceipt().fileName());
        for (var invalid : List.of(new MockMultipartFile("file", "bad.jpg", "image/jpeg", "text".getBytes()),
                new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[5_000_001])))
            assertThrows(IllegalArgumentException.class, () -> service.saveReceipt(tripId, id, invalid, creator.getUserId()));
        service.removeReceipt(tripId, id, creator.getUserId());
        assertNull(service.get(tripId, id, creator.getUserId()).getReceipt());
        service.saveReceipt(tripId, id, jpeg, creator.getUserId());
        service.delete(tripId, id, creator.getUserId());
        assertEquals(0, jdbc.queryForObject("select count(*) from expense_receipts", Integer.class));
    }

    @Test void tripCurrencyDefaultLegacyAndCreation() {
        assertEquals("AUD", trip.getBaseCurrency());
        jdbc.update("update trips set base_currency = null where trip_id = ?", trip.getTripId());
        assertEquals("AUD", tripService.getTripById(trip.getTripId(), owner.getUserId()).getBaseCurrency());
        CreateTripRequest r = new CreateTripRequest(); r.setName("USD trip"); r.setDestination("Test");
        r.setStartDate(LocalDate.now()); r.setEndDate(LocalDate.now()); r.setPlannedBudget(BigDecimal.TEN); r.setBaseCurrency("USD");
        var created = tripService.createTrip(r, owner.getUserId());
        assertEquals("USD", trips.findById(created.getTripId()).orElseThrow().getBaseCurrency());
        r.setBaseCurrency("BAD"); assertThrows(IllegalArgumentException.class, () -> tripService.createTrip(r, owner.getUserId()));
    }
    @Test void displayRateIsReadOnlyAndRequiresMembership() throws Exception {
        var saved = createExpense();
        mvc.perform(get("/api/trips/{id}/currency-rate?to=AUD", trip.getTripId()).with(authentication(auth(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rate").value(1));
        assertEquals("AUD", trips.findById(trip.getTripId()).orElseThrow().getBaseCurrency());
        mvc.perform(get("/api/trips/{id}/currency-rate?to=USD", trip.getTripId()).with(authentication(auth(owner))))
                .andExpect(status().isOk());
        var after = service.get(trip.getTripId(), saved.getExpenseId(), creator.getUserId());
        assertEquals(saved.getAmount(), after.getAmount()); assertEquals(saved.getOriginalAmount(), after.getOriginalAmount());
        assertEquals(0, saved.getExchangeRateToBase().compareTo(after.getExchangeRateToBase()));
        assertEquals(saved.getCurrentUserShare(), after.getCurrentUserShare());
        mvc.perform(get("/api/trips/{id}/currency-rate?to=AUD", trip.getTripId()).with(authentication(auth(outsider))))
                .andExpect(status().isForbidden());
    }
    void rate(String rate) {
        currencies.rate = new BigDecimal(rate);
    }
    @Test void foreignExpenseFreezesRateAndMetadataEditsPreserveShares() {
        rate("1.49");
        ExpenseRequest r = request("100"); r.setCurrencyCode("USD");
        r.setParticipants(List.of(participant(owner, null), participant(creator, null), participant(payer, null)));
        var created = service.create(trip.getTripId(), r, creator.getUserId());
        assertEquals(new BigDecimal("149.00"), created.getAmount());
        assertEquals(new BigDecimal("100.00"), created.getOriginalAmount());
        assertEquals(created.getAmount(), created.getShares().stream().map(ExpenseResponse.Share::getShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        rate("2.00"); r.setDescription("Updated description"); r.setCategory("Other");
        r.setSplitMode(ExpenseRequest.SplitMode.CUSTOM);
        r.setParticipants(created.getShares().stream().map(share -> {
            ExpenseRequest.Participant p = new ExpenseRequest.Participant(); p.setUserId(share.getUserId()); p.setShareAmount(share.getOriginalShareAmount()); return p;
        }).toList());
        var edited = service.update(trip.getTripId(), created.getExpenseId(), r, creator.getUserId());
        assertEquals(created.getAmount(), edited.getAmount());
        assertEquals(created.getShares().stream().map(ExpenseResponse.Share::getShareAmount).toList(), edited.getShares().stream().map(ExpenseResponse.Share::getShareAmount).toList());
        r.setDate(r.getDate().plusDays(1));
        assertEquals(new BigDecimal("200.00"), service.update(trip.getTripId(), created.getExpenseId(), r, creator.getUserId()).getAmount());
        r.setAmount(new BigDecimal("120")); r.setSplitMode(ExpenseRequest.SplitMode.EQUAL);
        assertEquals(new BigDecimal("240.00"), service.update(trip.getTripId(), created.getExpenseId(), r, creator.getUserId()).getAmount());
        r.setCurrencyCode("AUD");
        assertEquals(new BigDecimal("120.00"), service.update(trip.getTripId(), created.getExpenseId(), r, creator.getUserId()).getAmount());
    }
    @Test void foreignCustomSharesAreExactAndFailureRollsBack() {
        rate("1.495"); ExpenseRequest r = request("1.01"); r.setCurrencyCode("USD"); r.setSplitMode(ExpenseRequest.SplitMode.CUSTOM);
        r.setParticipants(List.of(participant(owner, "0.01"), participant(creator, "0.50"), participant(payer, "0.50")));
        var created = service.create(trip.getTripId(), r, creator.getUserId());
        assertEquals(new BigDecimal("1.51"), created.getAmount());
        assertEquals(created.getAmount(), created.getShares().stream().map(ExpenseResponse.Share::getShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        currencies.fail = true;
        r.setDate(r.getDate().plusDays(1));
        assertThrows(IllegalStateException.class, () -> service.update(trip.getTripId(), created.getExpenseId(), r, creator.getUserId()));
        assertThrows(IllegalStateException.class, () -> service.create(trip.getTripId(), r, creator.getUserId()));
        assertEquals(1, expenses.count());
        assertEquals(created.getAmount(), service.get(trip.getTripId(), created.getExpenseId(), creator.getUserId()).getAmount());
    }
    @Test void legacyCurrencyMetadataFallsBackWithoutChangingAccounting() {
        var created = createExpense();
        jdbc.update("update expenses set original_amount=null, currency_code=null, exchange_rate_to_base=null, exchange_rate_date=null where expense_id=?", created.getExpenseId());
        jdbc.update("update expense_shares set original_share_amount=null where expense_id=?", created.getExpenseId());
        var legacy = service.get(trip.getTripId(), created.getExpenseId(), creator.getUserId());
        assertEquals("AUD", legacy.getCurrencyCode()); assertEquals(legacy.getAmount(), legacy.getOriginalAmount());
        assertEquals(0, BigDecimal.ONE.compareTo(legacy.getExchangeRateToBase()));
        assertEquals(legacy.getShares().get(0).getShareAmount(), legacy.getShares().get(0).getOriginalShareAmount());
    }
    @Test void personalBudgetIsOwnUniqueFrozenAndTripScoped() throws Exception {
        assertNull(personalBudgetService.get(trip.getTripId(), creator.getUserId()));
        var request = new PersonalBudgetRequest(new BigDecimal("100"), "USD");
        rate("1.49");
        var first = personalBudgetService.save(trip.getTripId(), creator.getUserId(), request);
        assertEquals(new BigDecimal("149.00"), first.baseAmount());
        rate("2.00");
        assertEquals(first.baseAmount(), personalBudgetService.get(trip.getTripId(), creator.getUserId()).baseAmount());
        personalBudgetService.save(trip.getTripId(), creator.getUserId(), request);
        assertEquals(1, personalBudgets.count());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into personal_budgets (trip_id, user_id, amount, currency_code, exchange_rate_to_base, base_amount, exchange_rate_date, updated_at) "
                + "select trip_id, user_id, amount, currency_code, exchange_rate_to_base, base_amount, exchange_rate_date, updated_at from personal_budgets"));
        assertEquals(new BigDecimal("200.00"), personalBudgetService.get(trip.getTripId(), creator.getUserId()).baseAmount());
        assertNull(personalBudgetService.get(trip.getTripId(), owner.getUserId()));
        assertThrows(AccessDeniedException.class, () -> personalBudgetService.save(otherTrip.getTripId(), creator.getUserId(), request));
        assertThrows(AccessDeniedException.class, () -> personalBudgetService.get(trip.getTripId(), outsider.getUserId()));
        // Supplying someone else's ID cannot select or modify their personal budget.
        mvc.perform(put("/api/trips/{id}/personal-budget", trip.getTripId()).with(authentication(auth(owner)))
                .contentType("application/json").content("{\"amount\":30,\"currencyCode\":\"AUD\",\"userId\":" + creator.getUserId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.baseAmount").value(30));
        assertEquals(new BigDecimal("200.00"), personalBudgetService.get(trip.getTripId(), creator.getUserId()).baseAmount());
        assertEquals(2, personalBudgets.count());
        tripService.deleteTrip(trip.getTripId(), owner.getUserId()); assertEquals(0, personalBudgets.count());
    }
    @Test void personalBudgetFailureDoesNotOverwriteAndValidationIsSafe() {
        var request = new PersonalBudgetRequest(new BigDecimal("10"), "USD");
        personalBudgetService.save(trip.getTripId(), creator.getUserId(), request);
        currencies.fail = true;
        assertThrows(IllegalStateException.class, () -> personalBudgetService.save(trip.getTripId(), creator.getUserId(), request));
        assertEquals(new BigDecimal("14.90"), personalBudgetService.get(trip.getTripId(), creator.getUserId()).baseAmount());
        for (String amount : List.of("-1", "1.001", "100000000"))
            assertThrows(IllegalArgumentException.class, () -> personalBudgetService.save(trip.getTripId(), creator.getUserId(), new PersonalBudgetRequest(new BigDecimal(amount), "AUD")));
    }
    @Test void personalShareIsNotPayerTotalOrOutstandingDebt() {
        ExpenseRequest r = request("300");
        r.setParticipants(List.of(participant(owner, null), participant(creator, null), participant(payer, null)));
        var expense = service.create(trip.getTripId(), r, creator.getUserId());
        assertEquals(new BigDecimal("100.00"), service.get(trip.getTripId(), expense.getExpenseId(), payer.getUserId()).getCurrentUserShare());
        SettlementRequest payment = new SettlementRequest(); payment.setPayeeId(payer.getUserId()); payment.setAmount(new BigDecimal("40"));
        settlementService.create(trip.getTripId(), payment, creator.getUserId());
        assertEquals(new BigDecimal("60.00"), service.summary(trip.getTripId(), creator.getUserId()).getCurrentUserOwes());
        assertEquals(new BigDecimal("100.00"), service.get(trip.getTripId(), expense.getExpenseId(), creator.getUserId()).getCurrentUserShare());
    }
    @Test void emptyContext() { assertTrue(tripService.getUserTrips(user("new").getUserId()).isEmpty()); }
    @Test void oneContext() { assertEquals(List.of(trip.getTripId()), tripService.getUserTrips(creator.getUserId()).stream().map(TripResponse::getTripId).toList()); }
    @Test void multipleContexts() { join(otherTrip, creator, "MEMBER"); assertEquals(2, tripService.getUserTrips(creator.getUserId()).size()); }
    @ParameterizedTest @ValueSource(strings = {"INVITED", "PENDING", "DECLINED"})
    void inactiveMembershipCannotReadOrCreate(String status) {
        TripMember m = join(trip, outsider, "MEMBER"); m.setInviteStatus(status); memberships.save(m);
        assertEquals(1, tripService.getUserTrips(outsider.getUserId()).size());
        assertThrows(AccessDeniedException.class, () -> service.list(trip.getTripId(), outsider.getUserId()));
        assertThrows(AccessDeniedException.class, () -> service.create(trip.getTripId(), request("1"), outsider.getUserId()));
        assertFalse(memberService.getTripMembers(trip.getTripId(), creator.getUserId()).stream().anyMatch(x -> x.getUserId().equals(outsider.getUserId())));
    }
    @Test void creationPersistsDistinctPayerAndCreator() {
        ExpenseResponse result = createExpense();
        ExpenseResponse stored = service.get(trip.getTripId(), result.getExpenseId(), owner.getUserId());
        assertEquals(creator.getUserId(), stored.getCreatedById()); assertEquals(payer.getUserId(), stored.getPayerId());
        assertEquals(new BigDecimal("24.50"), stored.getAmount()); assertNotNull(stored.getCreatedAt());
        assertEquals(1, service.list(trip.getTripId(), payer.getUserId()).size());
    }
    @Test void nonMemberRejected() { assertThrows(AccessDeniedException.class, () -> service.list(trip.getTripId(), outsider.getUserId())); }
    @Test void payerMustBeActiveInSameTrip() {
        ExpenseRequest r = request("10"); r.setPayerId(outsider.getUserId());
        assertThrows(AccessDeniedException.class, () -> service.create(trip.getTripId(), r, creator.getUserId()));
        TripMember m = join(trip, outsider, "MEMBER"); m.setInviteStatus("INVITED"); memberships.save(m);
        assertThrows(AccessDeniedException.class, () -> service.create(trip.getTripId(), r, creator.getUserId()));
        assertEquals(0, expenses.count());
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.001", "100000000"})
    void invalidMoneyRejected(String amount) { assertThrows(IllegalArgumentException.class, () -> service.create(trip.getTripId(), request(amount), creator.getUserId())); }
    @Test void crossTripCrudCannotFindExpense() {
        ExpenseResponse result = createExpense(); join(otherTrip, creator, "MEMBER");
        assertThrows(ResourceNotFoundException.class, () -> service.get(otherTrip.getTripId(), result.getExpenseId(), creator.getUserId()));
        assertThrows(ResourceNotFoundException.class, () -> service.update(otherTrip.getTripId(), result.getExpenseId(), request("20"), creator.getUserId()));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(otherTrip.getTripId(), result.getExpenseId(), creator.getUserId()));
    }
    @Test void creatorCanUpdateAndDelete() {
        ExpenseResponse result = createExpense();
        assertEquals(new BigDecimal("20.00"), service.update(trip.getTripId(), result.getExpenseId(), request("20"), creator.getUserId()).getAmount());
        service.delete(trip.getTripId(), result.getExpenseId(), creator.getUserId()); assertEquals(0, expenses.count());
    }
    @Test void ownerCanUpdateAndDeleteWithoutChangingCreator() {
        ExpenseResponse result = createExpense();
        assertEquals(creator.getUserId(), service.update(trip.getTripId(), result.getExpenseId(), request("20"), owner.getUserId()).getCreatedById());
        service.delete(trip.getTripId(), result.getExpenseId(), owner.getUserId()); assertEquals(0, expenses.count());
    }
    @Test void payerIsNotAutomaticallyAnEditor() {
        ExpenseResponse result = createExpense();
        assertFalse(service.get(trip.getTripId(), result.getExpenseId(), payer.getUserId()).isCanEdit());
        assertThrows(AccessDeniedException.class, () -> service.update(trip.getTripId(), result.getExpenseId(), request("20"), payer.getUserId()));
        assertThrows(AccessDeniedException.class, () -> service.delete(trip.getTripId(), result.getExpenseId(), payer.getUserId()));
    }
    @Test void ownerUpdatesExistingTripBudget() {
        BudgetRequest r = new BudgetRequest(); r.setPlannedBudget(new BigDecimal("200.25"));
        assertThrows(AccessDeniedException.class, () -> service.updateBudget(trip.getTripId(), r, creator.getUserId()));
        service.updateBudget(trip.getTripId(), r, owner.getUserId());
        assertEquals(r.getPlannedBudget(), trips.findById(trip.getTripId()).orElseThrow().getPlannedBudget());
        assertEquals(r.getPlannedBudget(), service.budget(trip.getTripId(), creator.getUserId()).getPlannedBudget());
        r.setPlannedBudget(new BigDecimal("-1")); assertThrows(IllegalArgumentException.class, () -> service.updateBudget(trip.getTripId(), r, owner.getUserId()));
    }
    @Test void deletingTripAlsoDeletesExpenses() { createExpense(); tripService.deleteTrip(trip.getTripId(), owner.getUserId()); assertEquals(0, expenses.count()); }
    @Test void apiUsesPrincipalAndDoesNotExposeSecrets() throws Exception {
        String json = """
                {"payerId":%d,"createdById":%d,"createdBy":%d,"description":"Meal","amount":"12.34","date":"2026-09-05","category":"Food","splitMode":"EQUAL","participants":[{"userId":%d}]}
                """.formatted(payer.getUserId(), outsider.getUserId(), outsider.getUserId(), creator.getUserId());
        mvc.perform(post("/api/trips/{id}/expenses", trip.getTripId()).with(authentication(auth(creator))).contentType("application/json").content(json))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.createdById").value(creator.getUserId()))
                .andExpect(jsonPath("$.payerId").value(payer.getUserId())).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/trips/{id}/expenses", trip.getTripId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/trips/{id}/expenses", trip.getTripId()).with(authentication(auth(outsider)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/trips/{id}/expenses/999999", trip.getTripId()).with(authentication(auth(creator)))).andExpect(status().isNotFound());
        mvc.perform(post("/api/trips/{id}/expenses", trip.getTripId()).with(authentication(auth(creator))).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
    ExpenseRequest.Participant participant(User user, String amount) {
        ExpenseRequest.Participant p = new ExpenseRequest.Participant(); p.setUserId(user.getUserId());
        if (amount != null) p.setShareAmount(new BigDecimal(amount)); return p;
    }
    ExpenseResponse customExpense(String total, String first, String second) {
        ExpenseRequest r = request(total); r.setSplitMode(ExpenseRequest.SplitMode.CUSTOM);
        r.setParticipants(List.of(participant(creator, first), participant(payer, second)));
        return service.create(trip.getTripId(), r, creator.getUserId());
    }
    @ParameterizedTest @ValueSource(strings = {"24.00", "24.50", "0.03", "10.01"})
    void equalFourWaySplitIsExactAndStable(String amount) {
        join(trip, outsider, "MEMBER");
        ExpenseRequest r = request(amount);
        r.setParticipants(List.of(participant(outsider, null), participant(payer, null), participant(creator, null), participant(owner, null)));
        ExpenseResponse e = service.create(trip.getTripId(), r, creator.getUserId());
        List<ExpenseResponse.Share> shares = service.get(trip.getTripId(), e.getExpenseId(), creator.getUserId()).getShares();
        assertEquals(new BigDecimal(amount), shares.stream().map(ExpenseResponse.Share::getShareAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        long cents = new BigDecimal(amount).movePointRight(2).longValueExact();
        for (int i = 0; i < shares.size(); i++) assertEquals(BigDecimal.valueOf(cents / 4 + (i < cents % 4 ? 1 : 0), 2), shares.get(i).getShareAmount());
        assertEquals(4, jdbc.queryForObject("select count(*) from expense_shares where expense_id = ?", Integer.class, e.getExpenseId()));
    }
    @Test void oneParticipantGetsWholeAmount() {
        assertEquals(new BigDecimal("24.50"), createExpense().getShares().get(0).getShareAmount());
    }
    @ParameterizedTest @ValueSource(strings = {"EQUAL", "CUSTOM"})
    void invalidParticipantSetsRejected(String mode) {
        ExpenseRequest r = request("10"); r.setSplitMode(ExpenseRequest.SplitMode.valueOf(mode));
        r.setParticipants(List.of()); assertThrows(IllegalArgumentException.class, () -> service.create(trip.getTripId(), r, creator.getUserId()));
        r.setParticipants(List.of(participant(creator, "5"), participant(creator, "5")));
        assertThrows(IllegalArgumentException.class, () -> service.create(trip.getTripId(), r, creator.getUserId()));
        r.setParticipants(List.of(participant(outsider, "10")));
        assertThrows(AccessDeniedException.class, () -> service.create(trip.getTripId(), r, creator.getUserId()));
        TripMember pending = join(trip, outsider, "MEMBER"); pending.setInviteStatus("INVITED"); memberships.save(pending);
        assertThrows(AccessDeniedException.class, () -> service.create(trip.getTripId(), r, creator.getUserId()));
        assertEquals(0, expenses.count());
    }
    @Test void exactCustomAcceptedIncludingZero() {
        ExpenseResponse e = customExpense("10.00", "0.00", "10.00");
        assertEquals(List.of(new BigDecimal("0.00"), new BigDecimal("10.00")), e.getShares().stream().map(ExpenseResponse.Share::getShareAmount).toList());
    }
    @ParameterizedTest @ValueSource(strings = {"4.99", "5.01", "-1", "5.001"})
    void invalidCustomAmountsRejected(String share) {
        assertThrows(IllegalArgumentException.class, () -> customExpense("10.00", share, "5.00"));
        assertEquals(0, expenses.count());
    }
    @Test void missingCustomAmountRejected() {
        assertThrows(IllegalArgumentException.class, () -> customExpense("10.00", null, "10.00"));
    }
    @Test void updateRecalculatesAndRemovesObsoleteShares() {
        ExpenseResponse e = customExpense("10.00", "4.00", "6.00");
        ExpenseRequest r = request("24.50"); r.setParticipants(List.of(participant(creator, null), participant(owner, null)));
        service.update(trip.getTripId(), e.getExpenseId(), r, creator.getUserId());
        ExpenseResponse stored = service.get(trip.getTripId(), e.getExpenseId(), creator.getUserId());
        assertEquals(List.of(owner.getUserId(), creator.getUserId()), stored.getShares().stream().map(ExpenseResponse.Share::getUserId).toList());
        assertEquals(List.of(new BigDecimal("12.25"), new BigDecimal("12.25")), stored.getShares().stream().map(ExpenseResponse.Share::getShareAmount).toList());
        assertEquals(2, jdbc.queryForObject("select count(*) from expense_shares where expense_id = ?", Integer.class, e.getExpenseId()));
    }
    @Test void invalidUpdatePreservesExpenseAndShares() {
        ExpenseResponse e = createExpense();
        ExpenseRequest r = request("15"); r.setDescription("Changed"); r.setSplitMode(ExpenseRequest.SplitMode.CUSTOM);
        r.setParticipants(List.of(participant(payer, "14.99")));
        assertThrows(IllegalArgumentException.class, () -> service.update(trip.getTripId(), e.getExpenseId(), r, creator.getUserId()));
        ExpenseResponse stored = service.get(trip.getTripId(), e.getExpenseId(), creator.getUserId());
        assertEquals("Lunch", stored.getDescription()); assertEquals(new BigDecimal("24.50"), stored.getAmount());
        assertEquals(creator.getUserId(), stored.getShares().get(0).getUserId());
        assertEquals(new BigDecimal("24.50"), stored.getShares().get(0).getShareAmount());
    }
    @Test void databaseShareFailureRollsBackWholeMutation() {
        ExpenseResponse e = createExpense();
        jdbc.execute("alter table expense_shares add constraint test_share_failure check (share_amount <> 13.37)");
        try {
            ExpenseRequest r = request("13.37"); r.setDescription("Must roll back");
            assertThrows(RuntimeException.class, () -> service.update(trip.getTripId(), e.getExpenseId(), r, creator.getUserId()));
            assertThrows(RuntimeException.class, () -> service.create(trip.getTripId(), r, creator.getUserId()));
            ExpenseResponse stored = service.get(trip.getTripId(), e.getExpenseId(), creator.getUserId());
            assertEquals("Lunch", stored.getDescription()); assertEquals(new BigDecimal("24.50"), stored.getAmount());
            assertEquals(new BigDecimal("24.50"), stored.getShares().get(0).getShareAmount());
            assertEquals(1, expenses.count());
        } finally { jdbc.execute("alter table expense_shares drop constraint test_share_failure"); }
    }
    @Test void deletingExpenseRemovesShares() {
        ExpenseResponse e = createExpense(); service.delete(trip.getTripId(), e.getExpenseId(), creator.getUserId());
        assertEquals(0, jdbc.queryForObject("select count(*) from expense_shares", Integer.class));
    }
    @Test void databaseRejectsDuplicateShareRows() {
        ExpenseResponse e = createExpense();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into expense_shares(expense_id, user_id, share_amount) values (?, ?, 1)", e.getExpenseId(), creator.getUserId()));
    }
    @Test void emptySummary() {
        ExpenseSummaryResponse s = service.summary(trip.getTripId(), creator.getUserId());
        assertEquals(new BigDecimal("0.00"), s.getTotalSpending()); assertEquals(new BigDecimal("100.00"), s.getRemaining());
        assertEquals(new BigDecimal("0.00"), s.getCurrentUserOwes()); assertEquals(new BigDecimal("0.00"), s.getBudgetUsedPercentage());
    }
    @ParameterizedTest @ValueSource(strings = {"24.50", "100.00", "150.00"})
    void summaryTracksSpendingAndOverspend(String amount) {
        service.create(trip.getTripId(), request(amount), creator.getUserId());
        ExpenseSummaryResponse s = service.summary(trip.getTripId(), creator.getUserId());
        assertEquals(new BigDecimal(amount), s.getTotalSpending()); assertEquals(new BigDecimal("100.00").subtract(new BigDecimal(amount)), s.getRemaining());
        assertEquals(new BigDecimal(amount), s.getBudgetUsedPercentage()); assertEquals(new BigDecimal(amount), s.getCurrentUserOwes());
        assertEquals(new BigDecimal("0.00"), service.summary(trip.getTripId(), payer.getUserId()).getCurrentUserOwes());
    }
    @Test void zeroBudgetIsSafe() {
        BudgetRequest r = new BudgetRequest(); r.setPlannedBudget(BigDecimal.ZERO); service.updateBudget(trip.getTripId(), r, owner.getUserId());
        createExpense(); ExpenseSummaryResponse s = service.summary(trip.getTripId(), creator.getUserId());
        assertEquals(new BigDecimal("0.00"), s.getBudgetUsedPercentage()); assertEquals(new BigDecimal("-24.50"), s.getRemaining());
    }
    @Test void summaryNetsPaidAgainstAllocatedAndIsTripScoped() {
        customExpense("10.00", "5.00", "5.00");
        ExpenseRequest r = request("3.00"); r.setPayerId(creator.getUserId()); r.setParticipants(List.of(participant(payer, null)));
        service.create(trip.getTripId(), r, creator.getUserId());
        assertEquals(new BigDecimal("2.00"), service.summary(trip.getTripId(), creator.getUserId()).getCurrentUserOwes());
        assertEquals(new BigDecimal("0.00"), service.summary(otherTrip.getTripId(), outsider.getUserId()).getTotalSpending());
        assertThrows(AccessDeniedException.class, () -> service.summary(trip.getTripId(), outsider.getUserId()));
    }
    @Test void summaryAndMemberApiDenyCrossTripAndPendingAccess() throws Exception {
        mvc.perform(get("/api/trips/{id}/expenses/summary", trip.getTripId()).with(authentication(auth(creator))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalSpending").value(0));
        mvc.perform(get("/api/trips/{id}/members", trip.getTripId()).with(authentication(auth(outsider)))).andExpect(status().isForbidden());
        TripMember m = join(trip, outsider, "MEMBER"); m.setInviteStatus("INVITED"); memberships.save(m);
        mvc.perform(get("/api/trips/{id}/expenses/summary", trip.getTripId()).with(authentication(auth(outsider)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/trips/{id}/budget", trip.getTripId()).with(authentication(auth(outsider)))).andExpect(status().isForbidden());
    }
    UsernamePasswordAuthenticationToken auth(User user) {
        return new UsernamePasswordAuthenticationToken(new AuthenticatedUser(user.getUserId(), user.getEmail()), null, List.of());
    }
}
