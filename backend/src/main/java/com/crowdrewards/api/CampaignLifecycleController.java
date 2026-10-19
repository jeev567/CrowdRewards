package com.crowdrewards.api;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Read-only lifecycle projection. Uses total completed purchases, not distinct buyers.
 * Derived from durable payment/refund rows so restarts cannot desynchronize states.
 */
@RestController
@RequestMapping("/api/campaigns")
public class CampaignLifecycleController {
    private final JdbcTemplate jdbc;

    public CampaignLifecycleController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{campaignId}/lifecycle")
    public Map<String, Object> lifecycle(@PathVariable long campaignId) {
        return jdbc.query("""
            SELECT c.id, c.goal, c.joined,
                (SELECT COUNT(*) FROM checkout_orders o
                 WHERE o.campaign_id=c.id AND o.status='COMPLETED') AS captured,
                (SELECT COUNT(*) FROM campaign_refunds r
                 WHERE r.campaign_id=c.id) AS refunds_total,
                (SELECT COUNT(*) FROM campaign_refunds r
                 WHERE r.campaign_id=c.id AND r.status='COMPLETED') AS refunds_completed,
                (SELECT COUNT(*) FROM campaign_refunds r
                 WHERE r.campaign_id=c.id AND r.status IN ('FAILED','CANCELLED','NEEDS_ATTENTION')) AS refunds_failed
            FROM campaigns c WHERE c.id=?
            """, rs -> {
                if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found");
                int goal = rs.getInt("goal");
                int joined = rs.getInt("joined");
                int captured = rs.getInt("captured");
                int total = rs.getInt("refunds_total");
                int completed = rs.getInt("refunds_completed");
                int failed = rs.getInt("refunds_failed");
                String state;
                if (joined < goal) state = "ACTIVE";
                else if (failed > 0) state = "NEEDS_ATTENTION";
                else if (total == 0) state = "TARGET_REACHED";
                else if (completed == total) state = "COMPLETED";
                else state = "REFUNDING";
                return Map.<String, Object>of(
                    "campaignId", campaignId, "state", state,
                    "thresholdBasis", "TOTAL_SUCCESSFUL_PURCHASES",
                    "goal", goal, "joined", joined, "capturedOrders", captured,
                    "refundsTotal", total, "refundsCompleted", completed,
                    "refundsFailed", failed);
            }, campaignId);
    }
}
