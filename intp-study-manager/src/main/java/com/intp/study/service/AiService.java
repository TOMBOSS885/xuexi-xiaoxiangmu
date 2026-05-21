package com.intp.study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intp.study.dto.AiProviderDto;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Service
public class AiService {
    public static final String DEFAULT_MODEL = "gpt-5.5";

    private static final Map<String, String> PROVIDER_TYPES = Map.of(
            "openai_responses", "OpenAI Responses API",
            "openai_chat", "OpenAI 兼容 Chat Completions",
            "anthropic_messages", "Anthropic Messages API",
            "gemini_generate_content", "Google Gemini generateContent",
            "cohere_chat", "Cohere Chat API",
            "custom_http_json", "自定义 HTTP JSON",
            "minimax_chat", "MiniMax Chat API");

    private static final List<AiProviderDto> DEFAULT_PROVIDERS = List.of(
            provider("OpenAI Responses", "openai_responses", "https://api.openai.com/v1", DEFAULT_MODEL, "OPENAI_API_KEY", "bearer", "{}", "", "output_text"),
            provider("OpenAI 兼容接口", "openai_chat", "https://api.openai.com/v1", DEFAULT_MODEL, "OPENAI_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("本地 CLIProxyAPI", "openai_chat", "http://localhost:8317/v1", DEFAULT_MODEL, "CLIPROXY_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("幻城网安 API", "openai_chat", "https://api.iamhc.cn/v1", "auto", "IAMHC_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("DeepSeek V4 Pro", "openai_chat", "https://api.deepseek.com/v1", "deepseek-v4-pro", "DEEPSEEK_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("Anthropic Messages", "anthropic_messages", "https://api.anthropic.com", "claude-sonnet-4-5", "ANTHROPIC_API_KEY", "x-api-key", "{\"anthropic-version\":\"2023-06-01\"}", "", "content.0.text"),
            provider("Google Gemini", "gemini_generate_content", "https://generativelanguage.googleapis.com", "gemini-2.5-pro", "GEMINI_API_KEY", "query_key", "{}", "", "candidates.0.content.parts.0.text"),
            provider("MiniMax", "minimax_chat", "https://api.minimax.chat/v1", "MiniMax-M2.7", "MINIMAX_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("智谱 AI (GLM)", "openai_chat", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "ZHIPU_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("阿里云通义千问 (Qwen)", "openai_chat", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max", "DASHSCOPE_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("腾讯混元 (Hunyuan)", "openai_chat", "https://hunyuan.cloud.tencent.com/v1", "hunyuan-pro", "HUNYUAN_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("Cohere", "cohere_chat", "https://api.cohere.ai/v2", "command-r-plus", "COHERE_API_KEY", "bearer", "{}", "", "generations.0.text"),
            provider("Mistral AI", "openai_chat", "https://api.mistral.ai/v1", "mistral-large", "MISTRAL_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("Grok (xAI)", "openai_chat", "https://api.x.ai/v1", "grok-3", "XAI_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("硅基流动 (SiliconFlow)", "openai_chat", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-72B-Instruct", "SILICONFLOW_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("字节豆包 (Doubao)", "openai_chat", "https://ark.cn-beijing.volces.com/api/v3", "doubao-pro-32k", "DOUBAO_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("Kimi (Moonshot)", "openai_chat", "https://api.moonshot.cn/v1", "moonshot-v1-128k", "MOONSHOT_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("Groq", "openai_chat", "https://api.groq.com/openai/v1", "llama-4-scout", "GROQ_API_KEY", "bearer", "{}", "", "choices.0.message.content"),
            provider("Perplexity", "openai_chat", "https://api.perplexity.ai", "sonar-pro", "PERPLEXITY_API_KEY", "bearer", "{}", "", "choices.0.message.content"));

    private static final Pattern FENCED_THINK = Pattern.compile("(?is)<think>.*?</think>|<thinking>.*?</thinking>|\\[think].*?\\[/think]|\\[thinking].*?\\[/thinking]");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public AiService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.restTemplate = buildRestTemplate();
    }

    public void ensureDefaultApiProviders() {
        String sql = """
                INSERT OR IGNORE INTO api_providers (
                    name, provider_type, base_url, model, api_key_env, auth_type,
                    extra_headers_json, request_template_json, response_path, enabled
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """;
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                AiProviderDto provider = DEFAULT_PROVIDERS.get(i);
                ps.setString(1, provider.name());
                ps.setString(2, provider.providerType());
                ps.setString(3, provider.baseUrl());
                ps.setString(4, provider.model());
                ps.setString(5, provider.apiKeyEnv());
                ps.setString(6, provider.authType());
                ps.setString(7, provider.extraHeadersJson());
                ps.setString(8, provider.requestTemplateJson());
                ps.setString(9, provider.responsePath());
            }

            @Override
            public int getBatchSize() {
                return DEFAULT_PROVIDERS.size();
            }
        });
    }

    public List<AiProviderDto> listApiProviders(boolean enabledOnly) {
        ensureDefaultApiProviders();
        String where = enabledOnly ? "WHERE enabled = 1" : "";
        return jdbcTemplate.query("""
                SELECT *
                FROM api_providers
                %s
                ORDER BY id ASC
                """.formatted(where), providerRowMapper());
    }

    public List<Map<String, Object>> listProviders(boolean enabledOnly) {
        return listApiProviders(enabledOnly).stream()
                .map(this::providerToMap)
                .toList();
    }

    public Map<String, String> providerTypes() {
        return PROVIDER_TYPES;
    }

    public AiProviderDto getApiProvider(Integer providerId) {
        ensureDefaultApiProviders();
        List<AiProviderDto> rows = List.of();
        if (providerId != null) {
            rows = jdbcTemplate.query("SELECT * FROM api_providers WHERE id = ?", providerRowMapper(), providerId);
        }
        if (rows.isEmpty()) {
            rows = jdbcTemplate.query("SELECT * FROM api_providers WHERE enabled = 1 ORDER BY id ASC LIMIT 1", providerRowMapper());
        }
        if (rows.isEmpty()) {
            throw new AiServiceException("没有可用 API Provider，请先到 API 接入设置创建。");
        }
        return rows.get(0);
    }

    public long saveProvider(Map<String, String> form, Long providerId) {
        AiProviderDto provider = new AiProviderDto(
                providerId == null ? null : providerId.intValue(),
                form.get("name"),
                form.get("providerType"),
                form.get("baseUrl"),
                form.get("model"),
                form.get("apiKeyEnv"),
                form.get("authType"),
                form.getOrDefault("extraHeadersJson", "{}"),
                form.getOrDefault("requestTemplateJson", ""),
                form.getOrDefault("responsePath", ""),
                form.containsKey("enabled"));
        return saveApiProvider(provider, provider.id());
    }

    public int saveApiProvider(AiProviderDto data, Integer providerId) {
        String extraHeadersJson = normalizeJsonText(data.extraHeadersJson(), "{}");
        parseJsonObject(extraHeadersJson, "额外请求头");
        if (providerId != null) {
            jdbcTemplate.update("""
                    UPDATE api_providers
                    SET name = ?, provider_type = ?, base_url = ?, model = ?, api_key_env = ?,
                        auth_type = ?, extra_headers_json = ?, request_template_json = ?,
                        response_path = ?, enabled = ?, updated_at = datetime('now', 'localtime')
                    WHERE id = ?
                    """,
                    data.name(), data.providerType(), data.baseUrl(), data.model(), data.apiKeyEnv(),
                    defaultIfBlank(data.authType(), "bearer"), extraHeadersJson, data.requestTemplateJson(),
                    data.responsePath(), data.enabled() ? 1 : 0, providerId);
            return providerId;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO api_providers (
                        name, provider_type, base_url, model, api_key_env, auth_type,
                        extra_headers_json, request_template_json, response_path, enabled
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, data.name());
            ps.setString(2, data.providerType());
            ps.setString(3, data.baseUrl());
            ps.setString(4, data.model());
            ps.setString(5, data.apiKeyEnv());
            ps.setString(6, defaultIfBlank(data.authType(), "bearer"));
            ps.setString(7, extraHeadersJson);
            ps.setString(8, data.requestTemplateJson());
            ps.setString(9, data.responsePath());
            ps.setInt(10, data.enabled() ? 1 : 0);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.intValue();
    }

    public String generateText(
            String prompt,
            Integer providerId,
            String apiKey,
            String modelOverride,
            int maxOutputTokens,
            List<String> imagePaths,
            String reasoningDepth) {
        AiProviderDto provider = getApiProvider(providerId);
        String model = defaultIfBlank(modelOverride, defaultIfBlank(provider.model(), DEFAULT_MODEL));
        String key = resolveApiKey(provider, apiKey);
        AiRequestSpec request = buildRequest(provider, prompt, key, model, maxOutputTokens, imagePaths == null ? List.of() : imagePaths, reasoningDepth);

        String rawBody;
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    request.url(),
                    HttpMethod.valueOf(request.method()),
                    new HttpEntity<>(request.body(), request.headers()),
                    String.class);
            rawBody = response.getBody();
        } catch (HttpStatusCodeException exc) {
            throw buildHttpError(exc.getStatusCode().value(), exc.getResponseBodyAsString());
        } catch (ResourceAccessException exc) {
            throw new AiServiceException("API 请求失败：" + exc.getMessage(), "network", null, exc);
        } catch (RestClientException exc) {
            throw new AiServiceException("API 请求失败：" + exc.getMessage(), "unknown", null, exc);
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(defaultIfBlank(rawBody, "{}"), new TypeReference<>() {});
        } catch (JsonProcessingException exc) {
            throw new AiServiceException("API 没有返回 JSON：" + abbreviate(rawBody, 1200), "invalid_response", null, exc);
        }

        Object output = extractPath(payload, defaultIfBlank(provider.responsePath(), defaultResponsePath(provider.providerType())));
        if (output instanceof List<?> list) {
            output = list.stream().map(Objects::toString).collect(Collectors.joining("\n"));
        }
        if (output == null || output.toString().trim().isEmpty()) {
            Object reasoning = safeExtractPath(payload, "choices.0.message.reasoning_content");
            Object finishReason = safeExtractPath(payload, "choices.0.finish_reason");
            if (reasoning != null && "length".equals(Objects.toString(finishReason, ""))) {
                throw new AiServiceException("模型只返回了 reasoning，最终回答为空。请把最大输出 token 调高后重试。");
            }
            throw new AiServiceException("没有从响应路径中提取到文本：" + provider.responsePath());
        }

        String outputText = output.toString().trim();
        if ("minimax_chat".equals(provider.providerType()) || reasoningEnabled(reasoningDepth)) {
            outputText = stripThinkingContent(outputText, provider.providerType());
        }
        return outputText;
    }

    public String generateText(String prompt, Integer providerId, String apiKey, String modelOverride, int maxOutputTokens) {
        return generateText(prompt, providerId, apiKey, modelOverride, maxOutputTokens, List.of(), null);
    }

    public String generateText(String prompt, long providerId, String apiKey, String modelOverride, int maxOutputTokens) {
        return generateText(prompt, Math.toIntExact(providerId), apiKey, modelOverride, maxOutputTokens, List.of(), null);
    }

    public String providerLabel(AiProviderDto provider) {
        String typeLabel = PROVIDER_TYPES.getOrDefault(provider.providerType(), provider.providerType());
        String model = defaultIfBlank(provider.model(), "未设置模型");
        String state = provider.enabled() ? "启用" : "停用";
        return "#" + provider.id() + " · " + provider.name() + " · " + typeLabel + " · " + model + " · " + state;
    }

    public boolean isQuotaError(Exception exc) {
        if (exc instanceof AiServiceException aiExc && "quota".equals(aiExc.category())) {
            return true;
        }
        return "quota".equals(classifyApiErrorText(exc.getMessage(), null));
    }

    public List<String> listAvailableModels(AiProviderDto provider, String apiKey) {
        try {
            return switch (provider.providerType()) {
                case "openai_responses", "openai_chat" -> listOpenAiModels(provider.baseUrl(), resolveApiKeyForList(provider, apiKey));
                case "anthropic_messages" -> listAnthropicModels();
                case "gemini_generate_content" -> listGeminiModels(provider, apiKey);
                case "minimax_chat" -> listMinimaxModels(provider.baseUrl(), resolveApiKeyForList(provider, apiKey));
                case "cohere_chat" -> listCohereModels(provider.baseUrl(), resolveApiKeyForList(provider, apiKey));
                default -> List.of();
            };
        } catch (Exception exc) {
            return List.of();
        }
    }

    private AiRequestSpec buildRequest(
            AiProviderDto provider,
            String prompt,
            String apiKey,
            String model,
            int maxOutputTokens,
            List<String> imagePaths,
            String reasoningDepth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        parseJsonObject(provider.extraHeadersJson(), "额外请求头").forEach((key, value) -> headers.set(key, Objects.toString(value, "")));

        String url;
        Map<String, Object> body = new LinkedHashMap<>();
        switch (provider.providerType()) {
            case "openai_responses" -> {
                url = joinUrl(defaultIfBlank(provider.baseUrl(), "https://api.openai.com/v1"), "responses");
                body.put("model", model);
                body.put("input", prompt);
                body.put("max_output_tokens", maxOutputTokens);
            }
            case "openai_chat" -> {
                url = joinUrl(defaultIfBlank(provider.baseUrl(), "https://api.openai.com/v1"), "chat/completions");
                Object userContent = imagePaths.isEmpty() ? prompt : openAiImageContent(prompt, imagePaths);
                body.put("model", model);
                body.put("messages", List.of(Map.of("role", "user", "content", userContent)));
                body.put("temperature", 0.2);
                body.put("max_tokens", maxOutputTokens);
                if (reasoningEnabled(reasoningDepth)) {
                    body.put("reasoning_effort", reasoningEffort(reasoningDepth));
                }
            }
            case "anthropic_messages" -> {
                if (!imagePaths.isEmpty()) {
                    throw new AiServiceException("当前 Anthropic Provider 尚未实现图片直传，请改用 OpenAI 兼容视觉接口。");
                }
                String base = defaultIfBlank(provider.baseUrl(), "https://api.anthropic.com").replaceAll("/+$", "");
                if (base.endsWith("/v1/messages")) {
                    url = base;
                } else if (base.endsWith("/v1")) {
                    url = base + "/messages";
                } else {
                    url = base + "/v1/messages";
                }
                body.put("model", model);
                body.put("max_tokens", maxOutputTokens);
                body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
                if (reasoningEnabled(reasoningDepth)) {
                    body.put("thinking", Map.of("type", "enabled", "budget_tokens", reasoningBudget(reasoningDepth)));
                }
            }
            case "gemini_generate_content" -> {
                if (!imagePaths.isEmpty()) {
                    throw new AiServiceException("当前 Gemini Provider 尚未实现图片直传，请改用 OpenAI 兼容视觉接口。");
                }
                String base = defaultIfBlank(provider.baseUrl(), "https://generativelanguage.googleapis.com").replaceAll("/+$", "");
                url = base + "/v1beta/models/" + model + ":generateContent";
                body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));
                body.put("generationConfig", Map.of("maxOutputTokens", maxOutputTokens));
            }
            case "custom_http_json" -> {
                if (!imagePaths.isEmpty()) {
                    throw new AiServiceException("自定义 HTTP JSON Provider 尚未实现图片直传。");
                }
                url = provider.baseUrl();
                body.putAll(renderCustomBody(provider.requestTemplateJson(), prompt, model, maxOutputTokens));
            }
            case "minimax_chat" -> {
                url = joinUrl(defaultIfBlank(provider.baseUrl(), "https://api.minimax.chat/v1"), "chat/completions");
                body.put("model", model);
                body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
                body.put("temperature", 0.7);
                body.put("max_tokens", maxOutputTokens);
            }
            case "cohere_chat" -> {
                url = joinUrl(defaultIfBlank(provider.baseUrl(), "https://api.cohere.ai/v2"), "chat");
                body.put("model", model);
                body.put("message", prompt);
                body.put("max_tokens", maxOutputTokens);
                body.put("temperature", 0.7);
            }
            default -> throw new AiServiceException("未知 Provider 类型：" + provider.providerType());
        }

        if (isDeepSeek(provider, model)) {
            if (reasoningExplicitlyClosed(reasoningDepth)) {
                body.put("extra_body", Map.of("enable_thinking", false));
            } else if (reasoningEnabled(reasoningDepth)) {
                body.put("extra_body", Map.of("enable_thinking", true));
            }
        }

        AuthTarget authTarget = applyAuth(url, headers, provider.authType(), apiKey);
        return new AiRequestSpec("POST", authTarget.url(), authTarget.headers(), body);
    }

    private List<Map<String, Object>> openAiImageContent(String prompt, List<String> imagePaths) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        for (String imagePath : imagePaths) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageDataUri(Path.of(imagePath)))));
        }
        return content;
    }

    private String imageDataUri(Path path) {
        if (!Files.exists(path)) {
            throw new AiServiceException("页面图片不存在：" + path);
        }
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String mime = fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ? "image/jpeg" : "image/png";
        try {
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(path));
        } catch (IOException exc) {
            throw new AiServiceException("读取页面图片失败：" + path, "io", null, exc);
        }
    }

    private AuthTarget applyAuth(String url, HttpHeaders headers, String authType, String apiKey) {
        String normalized = defaultIfBlank(authType, "bearer");
        if ("bearer".equals(normalized) && !isBlank(apiKey)) {
            headers.setBearerAuth(apiKey);
        } else if ("x-api-key".equals(normalized) && !isBlank(apiKey)) {
            headers.set("x-api-key", apiKey);
        } else if ("api-key".equals(normalized) && !isBlank(apiKey)) {
            headers.set("api-key", apiKey);
        } else if ("x-goog-api-key".equals(normalized) && !isBlank(apiKey)) {
            headers.set("x-goog-api-key", apiKey);
        } else if ("query_key".equals(normalized) && !isBlank(apiKey)) {
            String sep = url.contains("?") ? "&" : "?";
            url = url + sep + "key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        } else if ("none".equals(normalized)) {
            return new AuthTarget(url, headers);
        } else {
            throw new AiServiceException("不支持的鉴权方式：" + authType);
        }
        return new AuthTarget(url, headers);
    }

    private String resolveApiKey(AiProviderDto provider, String apiKey) {
        String key = defaultIfBlank(apiKey, "");
        if (key.isBlank() && !provider.apiKeyEnv().isBlank()) {
            key = defaultIfBlank(System.getenv(provider.apiKeyEnv()), "");
        }
        if (key.isBlank() && "本地 CLIProxyAPI".equals(provider.name())) {
            key = "local-client-key";
        }
        if (key.isBlank() && !"none".equals(provider.authType())) {
            String target = provider.apiKeyEnv().isBlank() ? "页面临时密钥" : provider.apiKeyEnv();
            throw new AiServiceException("缺少 API Key。请在页面临时输入，或设置环境变量 " + target + "。");
        }
        return key;
    }

    private String resolveApiKeyForList(AiProviderDto provider, String apiKey) {
        try {
            return resolveApiKey(provider, apiKey);
        } catch (AiServiceException exc) {
            return "";
        }
    }

    private AiServiceException buildHttpError(int statusCode, String rawText) {
        String errorMessage = defaultIfBlank(rawText, "");
        String errorType = "";
        String errorCode = "";
        try {
            Map<String, Object> payload = objectMapper.readValue(defaultIfBlank(rawText, "{}"), new TypeReference<>() {});
            Object error = payload.getOrDefault("error", payload);
            if (error instanceof Map<?, ?> map) {
                errorMessage = Objects.toString(map.containsKey("message") ? map.get("message") : rawText, "").trim();
                errorType = Objects.toString(map.containsKey("type") ? map.get("type") : "", "").trim();
                errorCode = Objects.toString(map.containsKey("code") ? map.get("code") : "", "").trim();
            }
        } catch (JsonProcessingException ignored) {
            // Keep raw response text as the message.
        }

        String category = classifyApiErrorText(String.join(" ", errorMessage, errorType, errorCode, defaultIfBlank(rawText, "")), statusCode);
        String concise = compactApiErrorMessage(errorMessage);
        String suffix = "";
        if (!errorType.isBlank() || !errorCode.isBlank()) {
            List<String> parts = new ArrayList<>();
            if (!errorType.isBlank()) {
                parts.add("type=" + errorType);
            }
            if (!errorCode.isBlank()) {
                parts.add("code=" + errorCode);
            }
            suffix = "（" + String.join(", ", parts) + "）";
        }

        return switch (category) {
            case "quota" -> new AiServiceException(
                    "API 额度或上游余额不足（HTTP " + statusCode + "）" + suffix + "。\n"
                            + "上游返回：" + concise + "\n"
                            + "处理方式：切换到仍有额度的 Provider，或到该上游控制台充值 / 更换模型后再重试。",
                    category, statusCode, null);
            case "rate_limit" -> new AiServiceException(
                    "API 触发频率限制（HTTP " + statusCode + "）" + suffix + "。\n"
                            + "上游返回：" + concise + "\n"
                            + "处理方式：稍后重试，或把 PPT 逐页生成范围改成 10 页 / 20 页一组。",
                    category, statusCode, null);
            case "model_not_found" -> new AiServiceException(
                    "当前模型在这个 Provider 下不可用（HTTP " + statusCode + "）" + suffix + "。\n"
                            + "上游返回：" + concise + "\n"
                            + "处理方式：切换为该 Provider 实际支持的模型，或更换 Provider 后再重试。",
                    category, statusCode, null);
            case "model_incompatible" -> new AiServiceException(
                    "当前模型不支持本次请求形式（HTTP " + statusCode + "）" + suffix + "。\n"
                            + "上游返回：" + concise + "\n"
                            + "处理方式：如果正在发送页面图片，请切换到支持视觉输入的模型；否则更换兼容模型。",
                    category, statusCode, null);
            default -> new AiServiceException("API 返回 HTTP " + statusCode + suffix + "：" + compactApiErrorMessage(rawText), category, statusCode, null);
        };
    }

    private String classifyApiErrorText(String text, Integer statusCode) {
        String normalized = defaultIfBlank(text, "").toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "notenoughcverror", "insufficient_quota", "quota", "current quota", "balance", "prepaid", "余额不足", "额度不足", "资源不足", "11210")) {
            return "quota";
        }
        if ((statusCode != null && statusCode == 429)
                || containsAny(normalized, "rate_limit", "too many requests", "requests per", "rate limit", "限流", "请求过快", "频率限制")) {
            return "rate_limit";
        }
        if (containsAny(normalized, "model_not_found", "model not found", "no available channel for model", "no channel for model", "unsupported model", "模型不存在", "模型不可用", "没有可用渠道", "无可用渠道")) {
            return "model_not_found";
        }
        if (containsAny(normalized, "model_incompatible", "doesnt support image input", "doesn't support image input", "support image input", "unsupported image", "image input", "不支持图片", "不支持视觉")) {
            return "model_incompatible";
        }
        return "unknown";
    }

    private List<String> listOpenAiModels(String baseUrl, String apiKey) {
        if (isBlank(baseUrl) || isBlank(apiKey)) {
            return List.of();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        return extractModelIds(getJson(joinUrl(baseUrl, "models"), headers), "data", "id");
    }

    private List<String> listAnthropicModels() {
        return List.of(
                "claude-sonnet-4-7",
                "claude-sonnet-4-6",
                "claude-haiku-4-5",
                "claude-opus-4-7",
                "claude-3-5-sonnet-latest",
                "claude-3-5-haiku-latest",
                "claude-3-opus-latest",
                "claude-3-sonnet-latest",
                "claude-3-haiku-latest");
    }

    private List<String> listGeminiModels(AiProviderDto provider, String apiKey) {
        String key = resolveApiKeyForList(provider, apiKey);
        if (isBlank(key)) {
            return List.of();
        }
        String base = defaultIfBlank(provider.baseUrl(), "https://generativelanguage.googleapis.com").replaceAll("/+$", "");
        String url = base + "/v1beta/models?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        Map<String, Object> payload = getJson(url, new HttpHeaders());
        Object models = payload.get("models");
        if (!(models instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(item -> Objects.toString(item.get("name"), ""))
                .filter(name -> !name.isBlank())
                .map(name -> name.startsWith("models/") ? name.substring("models/".length()) : name)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> listMinimaxModels(String baseUrl, String apiKey) {
        if (isBlank(baseUrl) || isBlank(apiKey)) {
            return List.of();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        Map<String, Object> payload = getJson(joinUrl(baseUrl, "models"), headers);
        return extractModelIds(payload, "data", "id", "model_id", "name");
    }

    private List<String> listCohereModels(String baseUrl, String apiKey) {
        if (isBlank(baseUrl) || isBlank(apiKey)) {
            return List.of();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        Map<String, Object> payload = getJson(joinUrl(baseUrl, "models"), headers);
        return extractModelIds(payload, "models", "name", "id");
    }

    private Map<String, Object> getJson(String url, HttpHeaders headers) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return Map.of();
            }
            return objectMapper.readValue(defaultIfBlank(response.getBody(), "{}"), new TypeReference<>() {});
        } catch (Exception exc) {
            return Map.of();
        }
    }

    private List<String> extractModelIds(Map<String, Object> payload, String listKey, String... idKeys) {
        Object value = payload.get(listKey);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .flatMap(item -> {
                    for (String key : idKeys) {
                        String id = Objects.toString(item.get(key), "");
                        if (!id.isBlank()) {
                            return java.util.stream.Stream.of(id);
                        }
                    }
                    return java.util.stream.Stream.empty();
                })
                .distinct()
                .sorted()
                .toList();
    }

    private Map<String, Object> renderCustomBody(String template, String prompt, String model, int maxOutputTokens) {
        if (isBlank(template)) {
            throw new AiServiceException("自定义 HTTP JSON Provider 必须填写请求体模板。");
        }
        String rendered = template
                .replace("{prompt}", jsonStringContent(prompt))
                .replace("{model}", jsonStringContent(model))
                .replace("{max_output_tokens}", Integer.toString(maxOutputTokens));
        return parseJsonObject(rendered, "自定义请求体模板");
    }

    private Map<String, Object> parseJsonObject(String text, String label) {
        String normalized = normalizeJsonText(text, "{}");
        try {
            Object value = objectMapper.readValue(normalized, Object.class);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, val) -> result.put(Objects.toString(key), val));
                return result;
            }
        } catch (JsonProcessingException exc) {
            throw new AiServiceException(label + " 不是合法 JSON：" + exc.getOriginalMessage(), "invalid_json", null, exc);
        }
        throw new AiServiceException(label + " 必须是 JSON 对象。");
    }

    private String normalizeJsonText(String text, String defaultValue) {
        return isBlank(text) ? defaultValue : text.trim();
    }

    private Object extractPath(Object payload, String path) {
        if (isBlank(path)) {
            return payload;
        }
        Object current = payload;
        for (String part : path.split("\\.")) {
            if (current instanceof List<?> list) {
                try {
                    current = list.get(Integer.parseInt(part));
                } catch (NumberFormatException | IndexOutOfBoundsException exc) {
                    throw new AiServiceException("响应路径无法访问列表下标 " + part + "。", "invalid_response_path", null, exc);
                }
            } else if (current instanceof Map<?, ?> map) {
                if (!map.containsKey(part)) {
                    throw new AiServiceException("响应路径缺少字段 " + part + "。");
                }
                current = map.get(part);
            } else {
                throw new AiServiceException("响应路径在 " + part + " 处无法继续访问。");
            }
        }
        return current;
    }

    private Object safeExtractPath(Object payload, String path) {
        try {
            return extractPath(payload, path);
        } catch (AiServiceException exc) {
            return null;
        }
    }

    private String defaultResponsePath(String providerType) {
        return switch (providerType) {
            case "openai_responses" -> "output_text";
            case "openai_chat", "minimax_chat" -> "choices.0.message.content";
            case "anthropic_messages" -> "content.0.text";
            case "gemini_generate_content" -> "candidates.0.content.parts.0.text";
            case "cohere_chat" -> "generations.0.text";
            default -> "";
        };
    }

    private String stripThinkingContent(String text, String providerType) {
        if (isBlank(text)) {
            return text;
        }
        String stripped = FENCED_THINK.matcher(text).replaceAll("");
        stripped = stripped.replaceAll("(?is)\\[推理].*?\\[/推理]", "");
        stripped = stripped.replaceAll("(?im)^\\s*(思考过程|推理过程|分析过程|Thinking process|Let me think)[:：].*?(\\R\\R|\\z)", "");
        List<String> lines = new ArrayList<>();
        boolean skipUntilBlank = false;
        for (String line : stripped.split("\\R", -1)) {
            String normalized = line.trim().toLowerCase(Locale.ROOT);
            if (normalized.matches("^(思考中|推理中|分析中|正在分析|正在推理|thinking|analyzing).*")) {
                skipUntilBlank = true;
                continue;
            }
            if (skipUntilBlank) {
                if (normalized.isBlank()) {
                    skipUntilBlank = false;
                }
                continue;
            }
            lines.add(line);
        }
        return String.join("\n", lines).trim();
    }

    private RowMapper<AiProviderDto> providerRowMapper() {
        return (rs, rowNum) -> new AiProviderDto(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("provider_type"),
                rs.getString("base_url"),
                defaultIfBlank(rs.getString("model"), DEFAULT_MODEL),
                rs.getString("api_key_env"),
                defaultIfBlank(rs.getString("auth_type"), "bearer"),
                defaultIfBlank(rs.getString("extra_headers_json"), "{}"),
                rs.getString("request_template_json"),
                rs.getString("response_path"),
                rs.getInt("enabled") != 0);
    }

    private Map<String, Object> providerToMap(AiProviderDto provider) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", provider.id());
        row.put("name", provider.name());
        row.put("provider_type", provider.providerType());
        row.put("base_url", provider.baseUrl());
        row.put("model", provider.model());
        row.put("api_key_env", provider.apiKeyEnv());
        row.put("auth_type", provider.authType());
        row.put("extra_headers_json", provider.extraHeadersJson());
        row.put("request_template_json", provider.requestTemplateJson());
        row.put("response_path", provider.responsePath());
        row.put("enabled", provider.enabled() ? 1 : 0);
        return row;
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }

    private String joinUrl(String baseUrl, String path) {
        String base = defaultIfBlank(baseUrl, "").replaceAll("/+$", "");
        String cleanPath = path.replaceAll("^/+", "");
        if (base.endsWith(cleanPath)) {
            return base;
        }
        return base + "/" + cleanPath;
    }

    private String jsonStringContent(String value) {
        try {
            String encoded = objectMapper.writeValueAsString(value == null ? "" : value);
            return encoded.substring(1, encoded.length() - 1);
        } catch (JsonProcessingException exc) {
            throw new AiServiceException("JSON 字符串转义失败。", "invalid_json", null, exc);
        }
    }

    private String compactApiErrorMessage(String message) {
        String normalized = defaultIfBlank(message, "").replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "空响应";
        }
        return abbreviate(normalized, 600);
    }

    private String abbreviate(String value, int limit) {
        String text = defaultIfBlank(value, "");
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }

    private boolean containsAny(String text, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isDeepSeek(AiProviderDto provider, String model) {
        return provider.name().toLowerCase(Locale.ROOT).contains("deepseek") || model.toLowerCase(Locale.ROOT).contains("deepseek");
    }

    private boolean reasoningEnabled(String reasoningDepth) {
        String normalized = defaultIfBlank(reasoningDepth, "").toLowerCase(Locale.ROOT);
        return !normalized.isBlank() && !Set.of("关闭", "off", "none", "disabled", "false").contains(normalized);
    }

    private boolean reasoningExplicitlyClosed(String reasoningDepth) {
        String normalized = defaultIfBlank(reasoningDepth, "").toLowerCase(Locale.ROOT);
        return Set.of("关闭", "off", "none", "disabled", "false").contains(normalized);
    }

    private String reasoningEffort(String reasoningDepth) {
        return switch (defaultIfBlank(reasoningDepth, "中")) {
            case "低", "low" -> "low";
            case "高", "high" -> "high";
            default -> "medium";
        };
    }

    private int reasoningBudget(String reasoningDepth) {
        return switch (defaultIfBlank(reasoningDepth, "中")) {
            case "低", "low" -> 1024;
            case "高", "high" -> 10240;
            default -> 4096;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static AiProviderDto provider(String name, String type, String baseUrl, String model, String env, String auth, String extraHeaders, String requestTemplate, String responsePath) {
        return new AiProviderDto(null, name, type, baseUrl, model, env, auth, extraHeaders, requestTemplate, responsePath, true);
    }

    private record AiRequestSpec(String method, String url, HttpHeaders headers, Map<String, Object> body) {}

    private record AuthTarget(String url, HttpHeaders headers) {}

    public static class AiServiceException extends RuntimeException {
        private final String category;
        private final Integer statusCode;

        public AiServiceException(String message) {
            this(message, "unknown", null, null);
        }

        public AiServiceException(String message, String category, Integer statusCode, Throwable cause) {
            super(message, cause);
            this.category = category == null ? "unknown" : category;
            this.statusCode = statusCode;
        }

        public String category() {
            return category;
        }

        public Integer statusCode() {
            return statusCode;
        }
    }
}
