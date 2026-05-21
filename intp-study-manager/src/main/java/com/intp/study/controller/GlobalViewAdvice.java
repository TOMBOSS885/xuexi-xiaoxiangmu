package com.intp.study.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@ControllerAdvice
class GlobalViewAdvice {
    @ModelAttribute("today")
    String today() {
        return LocalDate.now().toString();
    }

    @ModelAttribute("reviewResults")
    List<String> reviewResults() {
        return Arrays.asList("完全掌握", "基本掌握", "仍然模糊", "完全不会");
    }

    @ModelAttribute("reviewStatuses")
    List<String> reviewStatuses() {
        return Arrays.asList("待复习", "已完成", "已跳过");
    }

    @ModelAttribute("errorCauseCategories")
    List<String> errorCauseCategories() {
        return Arrays.asList("概念不清", "公式记错", "条件漏看", "计算失误", "题型没识别", "思路方向错", "表达不严谨", "前置知识缺失");
    }

    @ModelAttribute("providerTypes")
    List<String> providerTypes() {
        return Arrays.asList("openai_responses", "openai_chat", "anthropic_messages", "gemini_generate_content", "minimax_chat", "cohere_chat", "custom_http_json");
    }

    @ModelAttribute("authTypes")
    List<String> authTypes() {
        return Arrays.asList("bearer", "x-api-key", "query_key", "none");
    }

    @ModelAttribute("deckStatuses")
    List<String> deckStatuses() {
        return Arrays.asList("使用中", "已归档", "待整理");
    }

    @ModelAttribute("questionStatuses")
    List<String> questionStatuses() {
        return Arrays.asList("未整理", "已整理", "转知识点", "待复习");
    }

    @ModelAttribute("parkingStatuses")
    List<String> parkingStatuses() {
        return Arrays.asList("未解决", "已解决", "已转知识点", "已转插问");
    }
}
