package com.intp.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SecretProviderDto(
        @JsonProperty("provider_id") int providerId,
        @JsonProperty("provider_name") String providerName,
        String model,
        @JsonProperty("provider_type") String providerType,
        @JsonProperty("base_url") String baseUrl,
        @JsonProperty("api_key") String apiKey,
        @JsonProperty("updated_at") String updatedAt
) {
    public SecretProviderDto {
        providerName = valueOrBlank(providerName);
        model = valueOrBlank(model);
        providerType = valueOrBlank(providerType);
        baseUrl = valueOrBlank(baseUrl);
        apiKey = valueOrBlank(apiKey);
        updatedAt = valueOrBlank(updatedAt);
    }

    private static String valueOrBlank(String value) {
        return value == null ? "" : value.trim();
    }
}
