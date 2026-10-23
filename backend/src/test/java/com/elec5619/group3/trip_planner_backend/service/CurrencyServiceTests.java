package com.elec5619.group3.trip_planner_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CurrencyServiceTests {
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final CurrencyService service = new CurrencyService(builder.build());
    final LocalDate date = LocalDate.of(2026, 9, 15);
    final String primary = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@2026-09-15/v1/currencies/usd.json";
    final String mirror = "https://2026-09-15.currency-api.pages.dev/v1/currencies/usd.json";
    final String json = "{\"date\":\"2026-09-15\",\"usd\":{\"aud\":1.495}}";
    @Test void sameCurrencyNeverCallsProvider() {
        assertEquals(BigDecimal.ONE, service.getRate("AUD", "AUD", date).rate()); server.verify();
    }
    @Test void primaryAndDecimalRounding() {
        server.expect(requestTo(primary)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertEquals(new BigDecimal("1.50"), service.convert(BigDecimal.ONE, "USD", "AUD", date)); server.verify();
    }
    @Test void fallback() {
        server.expect(requestTo(primary)).andRespond(withServerError());
        server.expect(requestTo(mirror)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertEquals(date, service.getRate("USD", "AUD", date).date()); server.verify();
    }
    @Test void missingOrWrongDateFailsSafely() {
        server.expect(requestTo(primary)).andRespond(withSuccess(json.replace("2026-09-15", "2026-09-14"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(mirror)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> service.getRate("USD", "AUD", date)); server.verify();
    }
    @Test void bothProvidersUnavailable() {
        server.expect(requestTo(primary)).andRespond(withServerError());
        server.expect(requestTo(mirror)).andRespond(withServerError());
        assertThrows(IllegalStateException.class, () -> service.getRate("USD", "AUD", date)); server.verify();
    }
    @Test void invalidCurrency() {
        for (String code : new String[]{null, "aud", "XXX", "../"}) assertThrows(IllegalArgumentException.class, () -> service.getRate(code, "AUD", date));
    }
}
