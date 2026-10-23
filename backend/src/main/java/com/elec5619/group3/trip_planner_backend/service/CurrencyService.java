package com.elec5619.group3.trip_planner_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import tools.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class CurrencyService {
    public static final List<String> CODES = List.of("AUD", "USD", "CNY", "EUR", "GBP", "JPY", "NZD", "CAD", "SGD", "HKD");
    private final RestClient client;

    public CurrencyService() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
        factory.setReadTimeout(Duration.ofSeconds(6));
        client = RestClient.builder().requestFactory(factory).build();
    }

    // Allows provider responses to be tested without external network calls.
    CurrencyService(RestClient client) { this.client = client; }

    public static String validateCode(String code) {
        if (code == null || !CODES.contains(code)) throw new IllegalArgumentException("Unsupported currency. Choose a supported three-letter currency code.");
        return code;
    }

    public record Quote(BigDecimal rate, LocalDate date) {}

    public Quote getRate(String from, String to, LocalDate date) {
        validateCode(from); validateCode(to);
        if (from.equals(to)) return new Quote(BigDecimal.ONE, date == null ? LocalDate.now() : date);
        String day = date == null ? "latest" : date.toString();
        String base = from.toLowerCase(Locale.ROOT);
        for (String url : List.of(
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@" + day + "/v1/currencies/" + base + ".json",
                "https://" + day + ".currency-api.pages.dev/v1/currencies/" + base + ".json")) {
            try {
                JsonNode body = client.get().uri(url).retrieve().body(JsonNode.class);
                LocalDate actualDate = LocalDate.parse(body.get("date").asText());
                if (date != null && !date.equals(actualDate)) throw new IllegalArgumentException("Wrong rate date");
                BigDecimal rate = new BigDecimal(body.get(base).get(to.toLowerCase(Locale.ROOT)).asText()).setScale(12, RoundingMode.HALF_UP);
                if (rate.signum() <= 0 || rate.precision() > 24) throw new IllegalArgumentException("Invalid rate");
                return new Quote(rate, actualDate);
            } catch (RuntimeException ignored) {
                // Invalid/missing data and network errors both try the provider mirror.
            }
        }
        throw new IllegalStateException("Exchange rate unavailable. Retry or enter the amount in the trip base currency.");
    }

    public BigDecimal convert(BigDecimal amount, String from, String to, LocalDate date) {
        return baseAmount(amount, getRate(from, to, date).rate());
    }

    public static BigDecimal baseAmount(BigDecimal amount, BigDecimal rate) {
        BigDecimal converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        if (converted.abs().compareTo(new BigDecimal("99999999.99")) > 0)
            throw new IllegalArgumentException("Converted amount exceeds the supported limit");
        return converted;
    }
}
