package com.intp.study.model;

import java.util.List;
import java.util.Map;

public final class AppConstants {
    public static final String DEFAULT_MODEL = "gpt-5.5";
    public static final int MASTERY_FORWARD_THRESHOLD = 70;

    public static final List<String> ERROR_CAUSE_CATEGORIES = List.of(
            "概念不清",
            "公式记错",
            "条件漏看",
            "计算失误",
            "题型没识别",
            "思路方向错",
            "表达不严谨",
            "前置知识缺失"
    );

    public static final List<ReviewInterval> REVIEW_INTERVALS = List.of(
            new ReviewInterval(1, "第 1 天复习"),
            new ReviewInterval(3, "第 3 天复习"),
            new ReviewInterval(7, "第 7 天复习"),
            new ReviewInterval(14, "第 14 天复习")
    );

    public static final List<String> REVIEW_RESULTS = List.of("完全掌握", "基本掌握", "仍然模糊", "完全不会");

    public static final Map<String, Integer> REVIEW_CHANGES = Map.of(
            "完全掌握", 15,
            "基本掌握", 5,
            "仍然模糊", -5,
            "完全不会", -15
    );

    private AppConstants() {
    }

    public record ReviewInterval(int days, String stage) {
    }
}
