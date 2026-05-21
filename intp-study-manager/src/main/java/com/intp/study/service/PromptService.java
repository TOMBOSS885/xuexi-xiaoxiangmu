package com.intp.study.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromptService {
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String loadTemplate(String name) {
        return cache.computeIfAbsent(name, this::readTemplate);
    }

    public String renderTemplate(String name, Map<String, ?> replacements) {
        String rendered = loadTemplate(name);
        for (Map.Entry<String, ?> entry : replacements.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", Objects.toString(entry.getValue(), ""));
        }
        return rendered;
    }

    public String formatStudyRecord(Map<String, ?> record) {
        return String.join("\n",
                "日期：" + value(record, "date"),
                "科目：" + value(record, "subject"),
                "章节 / 课程：" + value(record, "chapter"),
                "主题：" + value(record, "title"),
                "核心问题：" + value(record, "main_question"),
                "已掌握内容：" + value(record, "mastered_content"),
                "卡点：" + value(record, "blockers"),
                "错题或不会的问题：" + value(record, "wrong_questions"),
                "总结：" + value(record, "summary"),
                "掌握度：" + value(record, "mastery"));
    }

    public String formatKnowledgeCard(Map<String, ?> card) {
        return String.join("\n",
                "科目：" + value(card, "subject"),
                "知识点：" + value(card, "topic"),
                "核心问题：" + value(card, "core_question"),
                "一句话解释：" + value(card, "one_sentence"),
                "公式 / 逻辑推导：" + value(card, "logic_or_formula"),
                "典型题 / 应用场景：" + value(card, "application"),
                "掌握度：" + value(card, "mastery"));
    }

    private String readTemplate(String name) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + name);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception exc) {
            throw new IllegalArgumentException("Prompt 模板不存在：" + name, exc);
        }
    }

    private String value(Map<String, ?> source, String key) {
        return source == null ? "" : Objects.toString(source.get(key), "");
    }
}
