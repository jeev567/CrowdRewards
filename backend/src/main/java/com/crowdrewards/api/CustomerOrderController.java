package com.crowdrewards.api;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Sandbox-only receipt lookup. Order IDs are not authentication; do not expose
 * this endpoint publicly without a signed, scoped receipt token.
 */
@RestController
@RequestMapping("/api/checkout/orders")
public class CustomerOrderController {
    private final JdbcTemplate jdbc;
    public CustomerOrderController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/{orderId}/receipt")
    public Map<String, Object> receipt(@PathVariable String orderId) {
        if (!orderId.matches("[A-Za-z0-9]{8,40}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order ID");
        return jdbc.query("""
            SELECT o.paypal_order_id, o.campaign_id, o.amount_cents, o.status,
                   c.title, c.goal, c.joined, c.discount,
                   r.status AS refund_status, r.amount_cents AS refund_cents
            FROM checkout_orders o
            JOIN campaigns c ON c.id=o.campaign_id
            LEFT JOIN campaign_refunds r ON r.paypal_capture_id=o.paypal_capture_id
            WHERE o.paypal_order_id=?
            """, rs -> {
                if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
                long refundCents = rs.getLong("refund_cents");
                String refundStatus = rs.getString("refund_status");
                String paymentStatus = rs.getString("status");
                int joined = rs.getInt("joined"), goal = rs.getInt("goal");
                String customerStatus = !"COMPLETED".equals(paymentStatus) ? "PAYMENT_PENDING"
                    : refundStatus != null ? refundStatus
                    : joined >= goal ? "REFUND_PENDING" : "WAITING_FOR_GROUP";
                return Map.<String, Object>of(
                    "orderId", rs.getString("paypal_order_id"),
                    "campaignId", rs.getLong("campaign_id"),
                    "campaignTitle", rs.getString("title"),
                    "paidAmount", BigDecimal.valueOf(rs.getLong("amount_cents"), 2).toPlainString(),
                    "discountPercent", rs.getInt("discount"),
                    "joined", joined, "goal", goal,
                    "paymentStatus", paymentStatus,
                    "refundStatus", customerStatus,
                    "refundAmount", BigDecimal.valueOf(refundCents, 2).toPlainString());
            }, orderId);
    }
}
