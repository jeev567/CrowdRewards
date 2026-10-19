package com.crowdrewards;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CrowdRewardsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrowdRewardsApplication.class, args);
    }
}
