package com.crowdrewards.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/checkout")
public class PaypalCheckoutController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String clientId, secret, baseUrl, returnUrl;

    public PaypalCheckoutController(JdbcTemplate jdbc, ObjectMapper mapper,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            @Value("${paypal.sandbox.client-id:}") String clientId,
            @Value("${paypal.sandbox.client-secret:}") String secret,
            @Value("${paypal.base-url:https://api-m.sandbox.paypal.com}") String baseUrl,
            @Value("${paypal.return-url:http://localhost:5173/}") String returnUrl) {
        this.jdbc = jdbc; this.mapper = mapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.clientId = clientId; this.secret = secret; this.baseUrl = baseUrl; this.returnUrl = returnUrl;
    }

    public record CreateOrderRequest(long campaignId) {}
    public record OrderResult(String orderId, String approvalUrl) {}
    public record CaptureResult(String orderId, String status, long campaignId, int joined) {}

    @PostMapping("/orders")
    public OrderResult create(@RequestBody CreateOrderRequest request) throws Exception {
        requireConfigured();
        var rows = jdbc.query("SELECT title,price_cents,joined,goal FROM campaigns WHERE id=?",
            (rs, n) -> Map.of("title", rs.getString(1), "price", rs.getLong(2),
                "joined", rs.getInt(3), "goal", rs.getInt(4)), request.campaignId());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found");
        var campaign = rows.get(0);
        long cents = (long) campaign.get("price");
        if (cents <= 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Campaign needs a price before checkout");
        if ((int)campaign.get("joined") >= (int)campaign.get("goal"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Campaign goal already reached");
        String price = BigDecimal.valueOf(cents, 2).toPlainString();
        String payload = mapper.writeValueAsString(Map.of(
            "intent", "CAPTURE",
            "payment_source", Map.of("paypal", Map.of("experience_context", Map.of(
                "return_url", returnUrl, "cancel_url", returnUrl))),
            "purchase_units", new Object[]{Map.of("reference_id", String.valueOf(request.campaignId()),
                "description", campaign.get("title"),
                "amount", Map.of("currency_code", "USD", "value", price))}
        ));
        JsonNode response = paypal("POST", "/v2/checkout/orders", payload, "create-" + UUID.randomUUID());
        String orderId = response.path("id").asText("");
        String approval = "";
        for (JsonNode link : response.path("links"))
            if ("approve".equals(link.path("rel").asText())) approval = link.path("href").asText("");
        if (orderId.isBlank() || approval.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal did not provide an approval link");
        jdbc.update("INSERT INTO checkout_orders(paypal_order_id,campaign_id,amount_cents,status) VALUES (?,?,?,'CREATED')",
            orderId, request.campaignId(), cents);
        return new OrderResult(orderId, approval);
    }

    @PostMapping("/orders/{orderId}/capture")
    public CaptureResult capture(@PathVariable String orderId) throws Exception {
        requireConfigured();
        if (!orderId.matches("[A-Za-z0-9]{8,40}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PayPal order ID");
        var orders = jdbc.query("SELECT campaign_id,amount_cents,status FROM checkout_orders WHERE paypal_order_id=?",
            (rs,n) -> Map.of("campaignId",rs.getLong(1),"cents",rs.getLong(2),"status",rs.getString(3)), orderId);
        if (orders.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        var order = orders.get(0);
        long campaignId = (long)order.get("campaignId");
        if ("COMPLETED".equals(order.get("status"))) return completed(orderId,campaignId);

        // A stable request ID allows PayPal to deduplicate retries after a network failure.
        JsonNode result = paypal("POST", "/v2/checkout/orders/" + orderId + "/capture", "{}",
            "capture-" + orderId);
        if (!"COMPLETED".equals(result.path("status").asText()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PayPal payment not completed");
        JsonNode purchase = result.path("purchase_units").path(0);
        JsonNode captured = purchase.path("payments").path("captures").path(0);
        if (!"COMPLETED".equals(captured.path("status").asText())
                || !orderId.equals(result.path("id").asText())
                || !String.valueOf(campaignId).equals(purchase.path("reference_id").asText())
                || !"USD".equals(captured.path("amount").path("currency_code").asText())
                || BigDecimal.valueOf((long)order.get("cents"),2).compareTo(
                    new BigDecimal(captured.path("amount").path("value").asText("0"))) != 0)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayPal capture verification failed");

        tx.executeWithoutResult(status -> {
            int changed = jdbc.update("UPDATE checkout_orders SET status='COMPLETED',paypal_capture_id=? WHERE paypal_order_id=? AND status='CREATED'",
                captured.path("id").asText(), orderId);
            if (changed == 1)
                jdbc.update("UPDATE campaigns SET joined=joined+1 WHERE id=?", campaignId);
        });
        return completed(orderId,campaignId);
    }

    private CaptureResult completed(String orderId,long campaignId) {
        Integer joined = jdbc.queryForObject("SELECT joined FROM campaigns WHERE id=?",Integer.class,campaignId);
        return new CaptureResult(orderId,"COMPLETED",campaignId,joined == null ? 0 : joined);
    }

    private void requireConfigured() {
        if (clientId.isBlank() || secret.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PayPal Sandbox credentials not configured");
    }

    private JsonNode paypal(String method,String path,String payload,String requestId) throws Exception {
        String token = accessToken();
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30)).header("Authorization","Bearer " + token)
            .header("Content-Type","application/json").header("PayPal-Request-Id",requestId);
        HttpRequest request = builder.method(method,HttpRequest.BodyPublishers.ofString(payload)).build();
        HttpResponse<String> response = http.send(request,HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal API returned HTTP " + response.statusCode());
        return mapper.readTree(response.body());
    }

    private String accessToken() throws Exception {
        String credentials = Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/oauth2/token"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization","Basic " + credentials)
            .header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build();
        HttpResponse<String> response = http.send(request,HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()!=200)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal authentication failed");
        String token = mapper.readTree(response.body()).path("access_token").asText("");
        if (token.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal token missing");
        return token;
    }
}
