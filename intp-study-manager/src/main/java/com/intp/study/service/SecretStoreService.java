package com.intp.study.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SecretStoreService {
    private static final int KDF_ITERATIONS = 390_000;
    private static final int GCM_TAG_LENGTH = 128;

    private final ObjectMapper objectMapper;
    private final Path storePath;
    private final SecureRandom random = new SecureRandom();

    public SecretStoreService(ObjectMapper objectMapper, @Value("${app.data-dir:data}") String dataDir) {
        this.objectMapper = objectMapper;
        this.storePath = Path.of(dataDir).resolve("api_keys.enc.json").toAbsolutePath().normalize();
    }

    public boolean exists() {
        return Files.exists(storePath);
    }

    public boolean secretStoreExists() {
        return exists();
    }

    public Map<String, Object> load(String masterPassword) {
        if (!exists()) {
            return Map.of("providers", new LinkedHashMap<>());
        }
        if (masterPassword == null || masterPassword.isBlank()) {
            throw new SecretStoreException("请输入主密码。");
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(Files.readString(storePath, StandardCharsets.UTF_8), new TypeReference<>() {});
            byte[] salt = decode((String) payload.get("salt"));
            byte[] nonce = decode((String) payload.get("nonce"));
            byte[] ciphertext = decode((String) payload.get("ciphertext"));
            byte[] key = deriveKey(masterPassword, salt, intValue(payload.get("iterations"), KDF_ITERATIONS));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
            String json = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            Map<String, Object> data = objectMapper.readValue(json, new TypeReference<>() {});
            data.putIfAbsent("providers", new LinkedHashMap<>());
            return data;
        } catch (Exception exc) {
            throw new SecretStoreException("主密码不正确，或加密密钥库文件损坏。", exc);
        }
    }

    public Map<String, Object> loadSecretStore(String masterPassword) {
        return load(masterPassword);
    }

    public void save(String masterPassword, Map<String, Object> data) {
        if (masterPassword == null || masterPassword.isBlank()) {
            throw new SecretStoreException("请输入主密码。");
        }
        try {
            Files.createDirectories(storePath.getParent());
            byte[] salt = randomBytes(16);
            byte[] nonce = randomBytes(12);
            byte[] key = deriveKey(masterPassword, salt, KDF_ITERATIONS);
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("providers", data.getOrDefault("providers", new LinkedHashMap<>()));
            normalized.put("updated_at", LocalDateTime.now().withNano(0).toString());
            byte[] plaintext = objectMapper.writeValueAsBytes(normalized);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", 1);
            payload.put("algorithm", "AES-256-GCM");
            payload.put("kdf", "PBKDF2-HMAC-SHA256");
            payload.put("iterations", KDF_ITERATIONS);
            payload.put("salt", encode(salt));
            payload.put("nonce", encode(nonce));
            payload.put("ciphertext", encode(ciphertext));
            payload.put("public_index", buildPublicIndex(normalized));
            payload.put("updated_at", normalized.get("updated_at"));
            Files.writeString(storePath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload), StandardCharsets.UTF_8);
        } catch (Exception exc) {
            throw new SecretStoreException("保存加密密钥库失败。", exc);
        }
    }

    public void saveSecretStore(String masterPassword, Map<String, Object> data) {
        save(masterPassword, data);
    }

    public List<Map<String, Object>> loadSecretPublicIndex() {
        if (!exists()) {
            return List.of();
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(Files.readString(storePath, StandardCharsets.UTF_8), new TypeReference<>() {});
            Object publicIndex = payload.get("public_index");
            if (!(publicIndex instanceof Map<?, ?> index)) {
                return List.of();
            }
            Object providers = index.get("providers");
            if (!(providers instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(stringObjectMap(map));
                }
            }
            return result;
        } catch (Exception exc) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> upsertProviderSecret(Map<String, Object> data, long providerId, String providerName, String apiKey, String model, String providerType, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new SecretStoreException("API Key 不能为空。");
        }
        Map<String, Object> providers = new LinkedHashMap<>((Map<String, Object>) data.getOrDefault("providers", new LinkedHashMap<>()));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("provider_id", providerId);
        item.put("provider_name", providerName);
        item.put("model", model);
        item.put("provider_type", providerType);
        item.put("base_url", baseUrl);
        item.put("api_key", apiKey.strip());
        item.put("updated_at", LocalDateTime.now().withNano(0).toString());
        providers.put(String.valueOf(providerId), item);
        Map<String, Object> result = new LinkedHashMap<>(data == null ? Map.of() : data);
        result.put("providers", providers);
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> deleteProviderSecret(Map<String, Object> data, long providerId) {
        Map<String, Object> providers = new LinkedHashMap<>((Map<String, Object>) (data == null ? Map.of() : data).getOrDefault("providers", new LinkedHashMap<>()));
        providers.remove(String.valueOf(providerId));
        Map<String, Object> result = new LinkedHashMap<>(data == null ? Map.of() : data);
        result.put("providers", providers);
        return result;
    }

    public String getProviderSecret(Map<String, Object> data, long providerId) {
        if (data == null || !(data.get("providers") instanceof Map<?, ?> providers)) {
            return "";
        }
        Object item = providers.get(String.valueOf(providerId));
        if (!(item instanceof Map<?, ?> secret)) {
            return "";
        }
        return String.valueOf(secret.containsKey("api_key") ? secret.get("api_key") : "");
    }

    public String masked(String value) {
        if (value == null || value.isBlank()) {
            return "未保存";
        }
        return value.length() <= 10 ? "*".repeat(value.length()) : value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    public String maskedSecret(String value) {
        return masked(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPublicIndex(Map<String, Object> data) {
        Object rawProviders = data.getOrDefault("providers", Map.of());
        if (!(rawProviders instanceof Map<?, ?> providers)) {
            return Map.of("providers", List.of());
        }
        List<Map<String, Object>> publicProviders = new ArrayList<>();
        for (Map.Entry<?, ?> entry : providers.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            Map<String, Object> publicItem = new LinkedHashMap<>();
            publicItem.put("provider_id", item.containsKey("provider_id") ? item.get("provider_id") : entry.getKey());
            publicItem.put("provider_name", item.containsKey("provider_name") ? item.get("provider_name") : "");
            publicItem.put("model", item.containsKey("model") ? item.get("model") : "");
            publicItem.put("provider_type", item.containsKey("provider_type") ? item.get("provider_type") : "");
            publicItem.put("base_url", item.containsKey("base_url") ? item.get("base_url") : "");
            publicItem.put("updated_at", item.containsKey("updated_at") ? item.get("updated_at") : "");
            publicProviders.add(publicItem);
        }
        return Map.of("providers", publicProviders);
    }

    private Map<String, Object> stringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private byte[] deriveKey(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }

    private byte[] randomBytes(int length) {
        byte[] data = new byte[length];
        random.nextBytes(data);
        return data;
    }

    private String encode(byte[] data) {
        return Base64.getUrlEncoder().encodeToString(data);
    }

    private byte[] decode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    private int intValue(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exc) {
            return fallback;
        }
    }

    public static class SecretStoreException extends RuntimeException {
        public SecretStoreException(String message) {
            super(message);
        }

        public SecretStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
