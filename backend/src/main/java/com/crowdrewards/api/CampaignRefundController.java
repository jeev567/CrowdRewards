package com.crowdrewards.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignRefundController {
    private final CampaignRefundService refunds;

    public CampaignRefundController(CampaignRefundService refunds) {
        this.refunds = refunds;
    }

    @GetMapping("/{campaignId}/refunds")
    public List<Map<String, Object>> list(@PathVariable long campaignId) {
        return refunds.refundStatuses(campaignId);
    }
}
