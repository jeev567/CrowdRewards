package com.crowdrewards.api;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class SqliteMigration {
    @Bean
    ApplicationRunner addCheckoutSchema(JdbcTemplate jdbc) {
        return args -> {
            boolean exists = jdbc.query("PRAGMA table_info(campaigns)",
                rs -> { while(rs.next()) if ("price_cents".equals(rs.getString("name"))) return true; return false; });
            if (!exists) jdbc.execute("ALTER TABLE campaigns ADD COLUMN price_cents INTEGER NOT NULL DEFAULT 0");
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS checkout_orders (
                    paypal_order_id TEXT PRIMARY KEY,
                    campaign_id INTEGER NOT NULL,
                    amount_cents INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    paypal_capture_id TEXT UNIQUE,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS campaign_refunds (
                    paypal_capture_id TEXT PRIMARY KEY,
                    campaign_id INTEGER NOT NULL,
                    amount_cents INTEGER NOT NULL CHECK(amount_cents > 0),
                    status TEXT NOT NULL,
                    request_id TEXT NOT NULL UNIQUE,
                    paypal_refund_id TEXT UNIQUE,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (campaign_id) REFERENCES campaigns(id)
                )
                """);
            // Recover qualifying captures if the process stopped before enqueueing.
            jdbc.update("""
                INSERT OR IGNORE INTO campaign_refunds
                    (campaign_id, paypal_capture_id, amount_cents, status, request_id)
                SELECT c.id, o.paypal_capture_id,
                    (o.amount_cents * c.discount) / 100, 'QUEUED',
                    'crowdrewards-refund-' || o.paypal_capture_id
                FROM campaigns c JOIN checkout_orders o ON o.campaign_id = c.id
                WHERE c.joined >= c.goal AND o.status = 'COMPLETED'
                  AND o.paypal_capture_id IS NOT NULL
                  AND (o.amount_cents * c.discount) / 100 > 0
                """);
        };
    }
}
