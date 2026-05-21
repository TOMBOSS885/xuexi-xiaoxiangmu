package com.intp.study.dto;

public record AiProviderDto(
        Integer id,
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
    public AiProviderDto {
        name = valueOrBlank(name);
        providerType = valueOrBlank(providerType);
        baseUrl = valueOrBlank(baseUrl);
        model = valueOrBlank(model);
        apiKeyEnv = valueOrBlank(apiKeyEnv);
        authType = valueOrBlank(authType).isBlank() ? "bearer" : authType.trim();
        extraHeadersJson = valueOrBlank(extraHeadersJson).isBlank() ? "{}" : extraHeadersJson.trim();
        requestTemplateJson = valueOrBlank(requestTemplateJson);
        responsePath = valueOrBlank(responsePath);
    }

    private static String valueOrBlank(String value) {
        return value == null ? "" : value.trim();
    }
}
