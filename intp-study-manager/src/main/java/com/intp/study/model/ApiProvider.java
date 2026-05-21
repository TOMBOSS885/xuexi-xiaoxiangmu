package com.intp.study.model;

public record ApiProvider(
        Long id,
        String name,
        String providerType,
        String baseUrl,
        String model,
        String apiKeyEnv,
        String authType,
        String extraHeadersJson,
        String requestTemplateJson,
        String responsePath,
        boolean enabled
) {
}
