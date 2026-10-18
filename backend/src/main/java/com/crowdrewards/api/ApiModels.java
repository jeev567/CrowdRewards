package com.crowdrewards.api;

import jakarta.validation.constraints.*;

public final class ApiModels {
    private ApiModels() {}

    public record CreateMerchant(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email
    ) {}

    public record Merchant(long id, String name, String email) {}

    public record CreateCampaign(
        @NotNull @Positive Long merchantId,
        @NotBlank @Size(max = 80) String title,
        @Size(max = 500) String description,
        @NotNull @Min(1) @Max(90) Integer discount,
        @NotNull @Min(2) Integer goal
    ) {}

    public record UpdateCampaign(
        @NotBlank @Size(max = 80) String title,
        @Size(max = 500) String description,
        @NotNull @Min(1) @Max(90) Integer discount,
        @NotNull @Min(2) Integer goal
    ) {}

    public record Campaign(long id, long merchantId, String title,
                           String description, int discount, int goal, int joined) {}
}
