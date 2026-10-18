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
        };
    }
}
