package com.intp.study.service;

import com.intp.study.repository.SqlRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AppSettingService {
    private final SqlRepository repo;

    public AppSettingService(SqlRepository repo) {
        this.repo = repo;
    }

    public String get(String key, String defaultValue) {
        return repo.queryOne("SELECT value FROM app_settings WHERE key = ?", key)
                .map(row -> String.valueOf(row.get("value")))
                .orElse(defaultValue);
    }

    public void set(String key, String value) {
        repo.update("""
                INSERT INTO app_settings (key, value, updated_at)
                VALUES (?, ?, datetime('now', 'localtime'))
                ON CONFLICT(key) DO UPDATE SET
                    value = excluded.value,
                    updated_at = datetime('now', 'localtime')
                """, key, value == null ? "" : value);
    }

    public Map<String, Object> getDefaultApiConfig() {
        String providerId = get("default_api_provider_id", "");
        String model = get("default_api_model", "");
        return Map.of("providerId", providerId, "model", model);
    }
}
