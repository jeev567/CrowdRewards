package com.crowdrewards.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignRefundController {
    private final CampaignRefundService refunds;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public CampaignRefundController(CampaignRefundService refunds, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.refunds = refunds;
        this.jdbc = jdbc;
    }

    @GetMapping("/{campaignId}/refunds")
    public List<Map<String, Object>> list(@PathVariable long campaignId) {
        return refunds.refundStatuses(campaignId);
    }

    // Sandbox demo operator endpoint. Must be authenticated before public deployment.
    // Reuses the original PayPal-Request-Id, never generates a new refund key.
    @PostMapping("/{campaignId}/refunds/{captureId}/retry")
    public Map<String, String> retry(@PathVariable long campaignId, @PathVariable String captureId) {
        if (!captureId.matches("[A-Za-z0-9]{8,40}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid capture ID");
        int updated = jdbc.update("""
            UPDATE campaign_refunds SET status='QUEUED', attempts=0,
                last_error=NULL, updated_at=CURRENT_TIMESTAMP
            WHERE campaign_id=? AND paypal_capture_id=?
              AND status='NEEDS_ATTENTION'
            """, campaignId, captureId);
        if (updated != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Refund is not awaiting manual intervention");
        return Map.of("captureId", captureId, "status", "QUEUED");
    }
}
