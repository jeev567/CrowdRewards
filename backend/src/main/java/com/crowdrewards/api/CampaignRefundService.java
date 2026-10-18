package com.crowdrewards.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Service
@EnableScheduling
public class CampaignRefundService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String clientId, secret, baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public CampaignRefundService(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${paypal.sandbox.client-id:}") String clientId,
            @Value("${paypal.sandbox.client-secret:}") String secret,
            @Value("${paypal.base-url:https://api-m.sandbox.paypal.com}") String baseUrl) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clientId = clientId;
        this.secret = secret;
        this.baseUrl = baseUrl;
    }

    // Called only after a completed capture has been recorded. A unique capture ID
    // and INSERT OR IGNORE ensure a campaign cannot enqueue duplicate refunds.
    public void enqueueIfGoalReached(long campaignId) {
        jdbc.update("""
            INSERT OR IGNORE INTO campaign_refunds
                (campaign_id, paypal_capture_id, amount_cents, status, request_id)
            SELECT c.id, o.paypal_capture_id,
                   (o.amount_cents * c.discount) / 100, 'QUEUED',
                   'crowdrewards-refund-' || o.paypal_capture_id
            FROM campaigns c JOIN checkout_orders o ON o.campaign_id = c.id
            WHERE c.id = ? AND c.joined >= c.goal AND o.status = 'COMPLETED'
              AND o.paypal_capture_id IS NOT NULL
              AND (o.amount_cents * c.discount) / 100 > 0
            """, campaignId);
    }

    public java.util.List<Map<String, Object>> refundStatuses(long campaignId) {
        return jdbc.query("""
            SELECT paypal_capture_id, amount_cents, status, paypal_refund_id
            FROM campaign_refunds WHERE campaign_id=? ORDER BY created_at
            """, (rs, i) -> Map.<String, Object>of(
                "captureId", rs.getString(1),
                "amount", BigDecimal.valueOf(rs.getLong(2), 2).toPlainString(),
                "status", rs.getString(3),
                "refundId", rs.getString(4) == null ? "" : rs.getString(4)), campaignId);
    }

    // Retries are serialized by a database claim. Reuse the same PayPal-Request-Id
    // after uncertain network failures, rather than creating a second refund.
    @Scheduled(fixedDelay = 60000, initialDelay = 5000)
    public void processRefunds() {
        var pending = jdbc.query("""
            SELECT paypal_capture_id, amount_cents, request_id, paypal_refund_id, status
            FROM campaign_refunds
            WHERE status IN ('QUEUED','RETRY','PENDING')
               OR (status = 'PROCESSING' AND datetime(updated_at) < datetime('now','-3 minutes'))
            ORDER BY created_at LIMIT 25
            """, (rs, i) -> new RefundWork(rs.getString(1), rs.getLong(2),
                rs.getString(3), rs.getString(4), rs.getString(5)));
        for (RefundWork item : pending) {
            int claimed = jdbc.update("""
                UPDATE campaign_refunds SET status='PROCESSING', updated_at=CURRENT_TIMESTAMP
                WHERE paypal_capture_id=? AND status=? AND
                  (status <> 'PROCESSING' OR datetime(updated_at) < datetime('now','-3 minutes'))
                """, item.captureId(), item.status());
            if (claimed != 1) continue;
            try {
                JsonNode response;
                if (item.refundId() != null && !item.refundId().isBlank()) {
                    response = paypal("GET", "/v2/payments/refunds/" + item.refundId(), null, null);
                } else {
                    String body = mapper.writeValueAsString(Map.of("amount", Map.of(
                        "currency_code", "USD",
                        "value", BigDecimal.valueOf(item.cents(), 2).toPlainString())));
                    response = paypal("POST", "/v2/payments/captures/" + item.captureId()
                        + "/refund", body, item.requestId());
                }
                String refundId = response.path("id").asText("");
                String state = response.path("status").asText("");
                if (refundId.isBlank() || !("COMPLETED".equals(state) || "PENDING".equals(state)
                        || "FAILED".equals(state) || "CANCELLED".equals(state)))
                    throw new IllegalStateException("Unexpected PayPal refund response");
                jdbc.update("""
                    UPDATE campaign_refunds SET paypal_refund_id=?, status=?,
                        last_error=NULL, updated_at=CURRENT_TIMESTAMP
                    WHERE paypal_capture_id=?
                    """, refundId, state, item.captureId());
            } catch (Exception ex) {
                // Do not log credentials or the full PayPal response.
                jdbc.update("""
                    UPDATE campaign_refunds SET status='RETRY', attempts=attempts+1,
                        last_error=?, updated_at=CURRENT_TIMESTAMP
                    WHERE paypal_capture_id=?
                    """, ex.getClass().getSimpleName(), item.captureId());
            }
        }
    }

    private JsonNode paypal(String method, String path, String body, String requestId) throws Exception {
        if (clientId.isBlank() || secret.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PayPal Sandbox credentials missing");
        String basic = Base64.getEncoder().encodeToString(
            (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/oauth2/token"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Basic " + basic)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build();
        HttpResponse<String> tokenResponse = http.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        if (tokenResponse.statusCode() != 200) throw new IllegalStateException("PayPal authentication failed");
        String token = mapper.readTree(tokenResponse.body()).path("access_token").asText("");
        if (token.isBlank()) throw new IllegalStateException("PayPal access token missing");
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("Prefer", "return=representation");
        if (requestId != null) builder.header("PayPal-Request-Id", requestId);
        HttpRequest request = "GET".equals(method) ? builder.GET().build()
            : builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("PayPal refund API HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }

    private record RefundWork(String captureId, long cents, String requestId,
                              String refundId, String status) {}
}
