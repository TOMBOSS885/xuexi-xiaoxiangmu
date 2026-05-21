package com.intp.study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intp.study.dto.DailyReviewEvaluationDto;
import com.intp.study.dto.DailyReviewEvaluationItemDto;
import com.intp.study.dto.DailyReviewPlanDto;
import com.intp.study.dto.DailyReviewQuestionDto;
import com.intp.study.dto.MasteryUpdateDto;
import com.intp.study.dto.ReviewCandidateDto;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DailyReviewService {
    public static final int MAX_DAILY_REVIEW_QUESTIONS = 6;
    public static final int DEFAULT_DAILY_REVIEW_QUESTIONS = 5;

    private static final List<String> REVIEW_RESULTS = List.of("完全掌握", "基本掌握", "仍然模糊", "完全不会");
    private static final Set<String> ERROR_CAUSE_CATEGORIES = Set.of(
            "概念不清",
            "公式记错",
            "条件漏看",
            "计算失误",
            "题型没识别",
            "思路方向错",
            "表达不严谨",
            "前置知识缺失");
    private static final Pattern FENCED_JSON = Pattern.compile("(?is)```(?:json)?\\s*(.*?)```");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PromptService promptService;
    private final AiService aiService;

    public DailyReviewService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, PromptService promptService, AiService aiService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.promptService = promptService;
        this.aiService = aiService;
    }

    public Map<String, Object> getTodayAiReviewPlan() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM daily_ai_review_plans WHERE review_date = ?",
                today());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ReviewCandidateDto> collectReviewCandidates() {
        return collectReviewCandidates(MAX_DAILY_REVIEW_QUESTIONS);
    }

    public List<ReviewCandidateDto> collectReviewCandidates(int limit) {
        String sql = """
                SELECT *
                FROM (
                    SELECT
                        kc.id AS knowledge_id,
                        kc.subject,
                        kc.topic,
                        kc.core_question,
                        kc.one_sentence,
                        kc.logic_or_formula,
                        kc.application,
                        kc.mastery,
                        kc.need_review,
                        kc.created_at,
                        (
                            SELECT rt.id
                            FROM review_tasks rt
                            WHERE rt.knowledge_id = kc.id
                              AND rt.review_date <= ?
                              AND rt.status = '待复习'
                            ORDER BY rt.review_date ASC, rt.id ASC
                            LIMIT 1
                        ) AS task_id,
                        (
                            SELECT rt.review_stage
                            FROM review_tasks rt
                            WHERE rt.knowledge_id = kc.id
                              AND rt.review_date <= ?
                              AND rt.status = '待复习'
                            ORDER BY rt.review_date ASC, rt.id ASC
                            LIMIT 1
                        ) AS review_stage,
                        (
                            SELECT m.cause_category
                            FROM mistakes m
                            WHERE m.knowledge_id = kc.id OR (m.subject = kc.subject AND m.topic = kc.topic)
                            ORDER BY m.created_at DESC, m.id DESC
                            LIMIT 1
                        ) AS last_cause
                    FROM knowledge_cards kc
                )
                WHERE task_id IS NOT NULL OR mastery < 70 OR need_review = 1
                ORDER BY
                    CASE WHEN task_id IS NOT NULL THEN 0 ELSE 1 END,
                    mastery ASC,
                    created_at DESC,
                    knowledge_id DESC
                LIMIT ?
                """;
        return jdbcTemplate.queryForList(sql, today(), today(), Math.max(1, limit)).stream()
                .map(this::candidateForPrompt)
                .toList();
    }

    public Map<String, Object> generateTodayAiReviewPlan(int providerId, String apiKey, String model) {
        return generateTodayAiReviewPlan(providerId, apiKey, model, 1800);
    }

    public Map<String, Object> generateTodayAiReviewPlan(int providerId, String apiKey, String model, int maxOutputTokens) {
        List<ReviewCandidateDto> candidates = collectReviewCandidates();
        if (candidates.isEmpty()) {
            throw new DailyReviewException("今天没有可生成自测题的知识点。请先创建知识卡片，或等待复习任务到期。");
        }

        int maxQuestions = Math.min(Math.min(DEFAULT_DAILY_REVIEW_QUESTIONS, MAX_DAILY_REVIEW_QUESTIONS), candidates.size());
        String prompt = promptService.renderTemplate("daily_ai_review_plan.md", Map.of(
                "today", today(),
                "max_questions", Integer.toString(maxQuestions),
                "candidates_json", writePretty(candidates)));
        String raw = aiService.generateText(prompt, providerId, apiKey, model, maxOutputTokens);
        DailyReviewPlanDto plan = normalizePlanPayload(loadJsonPayload(raw), candidates, maxQuestions);
        saveTodayPlan(providerId, model, plan, candidates, "待回答");
        Map<String, Object> stored = getTodayAiReviewPlan();
        if (stored == null) {
            throw new DailyReviewException("自测计划已生成，但读取失败。");
        }
        return stored;
    }

    public Map<String, Object> regenerateTodayAiReviewPlan(int providerId, String apiKey, String model, int maxOutputTokens) {
        jdbcTemplate.update("DELETE FROM daily_ai_review_plans WHERE review_date = ?", today());
        return generateTodayAiReviewPlan(providerId, apiKey, model, maxOutputTokens);
    }

    public DailyReviewEvaluationDto evaluateTodayAiReview(
            Map<String, Object> planRow,
            Map<String, String> answers,
            int providerId,
            String apiKey,
            String model,
            int maxOutputTokens) {
        DailyReviewPlanDto plan = readJson(Objects.toString(planRow.get("plan_json"), "{}"), DailyReviewPlanDto.class);
        Map<String, String> normalizedAnswers = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : answers.entrySet()) {
            String questionId = Objects.toString(entry.getKey(), "").trim();
            if (!questionId.isBlank()) {
                normalizedAnswers.put(questionId, Objects.toString(entry.getValue(), "").trim());
            }
        }

        String prompt = promptService.renderTemplate("daily_ai_review_grade.md", Map.of(
                "today", today(),
                "plan_json", writePretty(plan),
                "answers_json", writePretty(normalizedAnswers)));
        String raw = aiService.generateText(prompt, providerId, apiKey, model, maxOutputTokens);
        DailyReviewEvaluationDto evaluation = normalizeEvaluationPayload(loadJsonPayload(raw), plan, normalizedAnswers);
        List<MasteryUpdateDto> updates = applyEvaluationResults(plan, evaluation);
        DailyReviewEvaluationDto withUpdates = new DailyReviewEvaluationDto(evaluation.overallSummary(), evaluation.evaluations(), updates);

        jdbcTemplate.update("""
                UPDATE daily_ai_review_plans
                SET answers_json = ?,
                    evaluation_json = ?,
                    status = '已批改',
                    evaluated_at = datetime('now', 'localtime')
                WHERE id = ?
                """,
                writeJson(normalizedAnswers),
                writeJson(withUpdates),
                planRow.get("id"));
        return withUpdates;
    }

    public DailyReviewEvaluationDto evaluateTodayAiReview(Map<String, Object> planRow, Map<String, String> answers, int providerId, String apiKey, String model) {
        return evaluateTodayAiReview(planRow, answers, providerId, apiKey, model, 2200);
    }

    public DailyReviewPlanDto planPayload(Map<String, Object> planRow) {
        return readJson(Objects.toString(planRow.getOrDefault("plan_json", "{}")), DailyReviewPlanDto.class);
    }

    public DailyReviewEvaluationDto evaluationPayload(Map<String, Object> planRow) {
        String text = Objects.toString(planRow.getOrDefault("evaluation_json", ""), "");
        if (text.isBlank()) {
            return null;
        }
        return readJson(text, DailyReviewEvaluationDto.class);
    }

    public Map<String, String> answersPayload(Map<String, Object> planRow) {
        String text = Objects.toString(planRow.getOrDefault("answers_json", "{}"), "{}");
        Map<String, Object> raw = readJsonMap(text);
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(key, Objects.toString(value, "")));
        return result;
    }

    private void saveTodayPlan(int providerId, String model, DailyReviewPlanDto plan, List<ReviewCandidateDto> candidates, String status) {
        jdbcTemplate.update("""
                INSERT INTO daily_ai_review_plans (
                    review_date, provider_id, model, plan_json, source_snapshot_json, status
                )
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(review_date) DO UPDATE SET
                    provider_id = excluded.provider_id,
                    model = excluded.model,
                    plan_json = excluded.plan_json,
                    source_snapshot_json = excluded.source_snapshot_json,
                    answers_json = '{}',
                    evaluation_json = '',
                    status = excluded.status,
                    created_at = datetime('now', 'localtime'),
                    evaluated_at = ''
                """,
                today(),
                providerId,
                model,
                writeJson(plan),
                writeJson(candidates),
                status);
    }

    private ReviewCandidateDto candidateForPrompt(Map<String, Object> row) {
        int mastery = intValue(row.get("mastery"), 0);
        return new ReviewCandidateDto(
                intValue(row.get("knowledge_id"), 0),
                nullableInt(row.get("task_id")),
                Objects.toString(row.get("subject"), ""),
                Objects.toString(row.get("topic"), ""),
                Objects.toString(row.get("core_question"), ""),
                Objects.toString(row.get("one_sentence"), ""),
                clip(Objects.toString(row.get("logic_or_formula"), ""), 500),
                clip(Objects.toString(row.get("application"), ""), 400),
                mastery,
                defaultIfBlank(Objects.toString(row.get("review_stage"), ""), mastery < 70 ? "低掌握度重点复习" : "常规复习"),
                Objects.toString(row.get("last_cause"), ""));
    }

    private DailyReviewPlanDto normalizePlanPayload(Map<String, Object> payload, List<ReviewCandidateDto> candidates, int maxQuestions) {
        Set<Integer> candidateIds = candidates.stream().map(ReviewCandidateDto::knowledgeId).collect(Collectors.toCollection(LinkedHashSet::new));
        Object rawQuestions = payload.get("questions");
        List<DailyReviewQuestionDto> questions = new ArrayList<>();
        if (rawQuestions instanceof List<?> list) {
            for (Object rawItem : list) {
                if (!(rawItem instanceof Map<?, ?> item)) {
                    continue;
                }
                Integer knowledgeId = nullableInt(item.get("knowledge_id"));
                if (knowledgeId == null || !candidateIds.contains(knowledgeId)) {
                    continue;
                }
                String question = Objects.toString(item.get("question"), "").trim();
                if (question.isBlank()) {
                    continue;
                }
                List<String> expectedPoints = stringList(item.get("expected_points"));
                questions.add(new DailyReviewQuestionDto(
                        defaultIfBlank(Objects.toString(item.get("question_id"), ""), "q" + (questions.size() + 1)),
                        knowledgeId,
                        defaultIfBlank(Objects.toString(item.get("topic"), ""), topicForCandidate(candidates, knowledgeId)),
                        defaultIfBlank(Objects.toString(item.get("question_type"), ""), "概念解释题"),
                        question,
                        expectedPoints));
                if (questions.size() >= maxQuestions) {
                    break;
                }
            }
        }

        if (questions.isEmpty()) {
            int index = 1;
            for (ReviewCandidateDto candidate : candidates.stream().limit(maxQuestions).toList()) {
                questions.add(new DailyReviewQuestionDto(
                        "q" + index,
                        candidate.knowledgeId(),
                        candidate.topic(),
                        "概念解释题",
                        "闭卷解释：" + candidate.topic() + " 想解决什么核心问题？它和前面学过的哪些概念容易混淆？",
                        List.of(defaultIfBlank(candidate.coreQuestion(), "说明核心问题"), defaultIfBlank(candidate.oneSentence(), "给出一句话解释"))));
                index++;
            }
        }

        String mainLine = defaultIfBlank(Objects.toString(payload.get("main_line"), ""), "今天用少量问题检查到期复习和低掌握度知识点。");
        return new DailyReviewPlanDto(mainLine, questions);
    }

    private DailyReviewEvaluationDto normalizeEvaluationPayload(Map<String, Object> payload, DailyReviewPlanDto plan, Map<String, String> answers) {
        Map<String, DailyReviewQuestionDto> questionById = plan.questions().stream()
                .collect(Collectors.toMap(DailyReviewQuestionDto::questionId, item -> item, (a, b) -> a, LinkedHashMap::new));
        List<DailyReviewEvaluationItemDto> evaluations = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        Object rawEvaluations = payload.get("evaluations");
        if (rawEvaluations instanceof List<?> list) {
            for (Object rawItem : list) {
                if (!(rawItem instanceof Map<?, ?> item)) {
                    continue;
                }
                String questionId = Objects.toString(item.get("question_id"), "").trim();
                DailyReviewQuestionDto question = questionById.get(questionId);
                if (question == null) {
                    continue;
                }
                seen.add(questionId);
                int score = clampInt(item.get("score"), 0);
                String result = normalizeResult(item.get("result"), score);
                evaluations.add(new DailyReviewEvaluationItemDto(
                        questionId,
                        question.knowledgeId(),
                        score,
                        result,
                        normalizeCause(item.get("cause_category")),
                        Objects.toString(item.get("feedback"), "").trim(),
                        Objects.toString(item.get("correct_answer"), "").trim(),
                        Objects.toString(item.get("next_question"), "").trim()));
            }
        }

        for (DailyReviewQuestionDto question : questionById.values()) {
            if (seen.contains(question.questionId())) {
                continue;
            }
            String answer = Objects.toString(answers.get(question.questionId()), "").trim();
            int score = answer.isBlank() ? 0 : 50;
            evaluations.add(new DailyReviewEvaluationItemDto(
                    question.questionId(),
                    question.knowledgeId(),
                    score,
                    normalizeResult(null, score),
                    answer.isBlank() ? "前置知识缺失" : "概念不清",
                    "未获得有效批改，已按回答完整度保守估计。",
                    "请重新生成或手动复盘本题。",
                    "这个知识点想解决什么核心问题？"));
        }

        String summary = defaultIfBlank(Objects.toString(payload.get("overall_summary"), ""), "已根据本次自测更新掌握度。");
        return new DailyReviewEvaluationDto(summary, evaluations, List.of());
    }

    private List<MasteryUpdateDto> applyEvaluationResults(DailyReviewPlanDto plan, DailyReviewEvaluationDto evaluation) {
        Map<String, Object> planRow = getTodayAiReviewPlan();
        List<ReviewCandidateDto> sourceItems = readJsonList(
                Objects.toString(planRow == null ? "[]" : planRow.getOrDefault("source_snapshot_json", "[]")),
                new TypeReference<List<ReviewCandidateDto>>() {});
        Map<Integer, ReviewCandidateDto> sourceByKnowledgeId = sourceItems.stream()
                .collect(Collectors.toMap(ReviewCandidateDto::knowledgeId, item -> item, (a, b) -> a, LinkedHashMap::new));
        Map<Integer, List<DailyReviewEvaluationItemDto>> grouped = evaluation.evaluations().stream()
                .collect(Collectors.groupingBy(DailyReviewEvaluationItemDto::knowledgeId, LinkedHashMap::new, Collectors.toList()));

        List<MasteryUpdateDto> updates = new ArrayList<>();
        for (Map.Entry<Integer, List<DailyReviewEvaluationItemDto>> entry : grouped.entrySet()) {
            int knowledgeId = entry.getKey();
            List<DailyReviewEvaluationItemDto> items = entry.getValue();
            int averageScore = Math.round((float) items.stream().mapToInt(DailyReviewEvaluationItemDto::score).average().orElse(0));
            String result = normalizeResult(null, averageScore);
            ReviewCandidateDto source = sourceByKnowledgeId.get(knowledgeId);
            int before = queryKnowledgeMastery(knowledgeId);
            Integer taskId = source == null ? null : source.taskId();
            if (taskId != null) {
                markReviewResult(taskId, result);
            } else {
                int after = applyReviewResult(before, result);
                jdbcTemplate.update(
                        "UPDATE knowledge_cards SET mastery = ?, need_review = ? WHERE id = ?",
                        after,
                        after < 70 || Set.of("仍然模糊", "完全不会").contains(result) ? 1 : 0,
                        knowledgeId);
                if ("仍然模糊".equals(result)) {
                    createExtraReview(knowledgeId, 2, "AI 追加复习：2 天后");
                } else if ("完全不会".equals(result)) {
                    createExtraReview(knowledgeId, 1, "AI 重点突破：1 天后");
                }
            }
            int after = queryKnowledgeMastery(knowledgeId);
            updates.add(new MasteryUpdateDto(
                    knowledgeId,
                    source == null ? topicForPlan(plan, knowledgeId) : source.topic(),
                    averageScore,
                    result,
                    before,
                    after));
        }
        return updates;
    }

    private void markReviewResult(int taskId, String result) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT rt.*, kc.mastery
                FROM review_tasks rt
                JOIN knowledge_cards kc ON kc.id = rt.knowledge_id
                WHERE rt.id = ?
                """, taskId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> task = rows.get(0);
        int newMastery = applyReviewResult(intValue(task.get("mastery"), 0), result);
        jdbcTemplate.update("UPDATE review_tasks SET status = '已完成', result = ? WHERE id = ?", result, taskId);
        jdbcTemplate.update("UPDATE knowledge_cards SET mastery = ? WHERE id = ?", newMastery, task.get("knowledge_id"));
        if ("仍然模糊".equals(result)) {
            createExtraReview(intValue(task.get("knowledge_id"), 0), 2, "追加复习：2 天后");
        } else if ("完全不会".equals(result)) {
            createExtraReview(intValue(task.get("knowledge_id"), 0), 1, "重点突破：1 天后");
        }
    }

    private void createExtraReview(int knowledgeId, int days, String stage) {
        jdbcTemplate.update("""
                INSERT INTO review_tasks (knowledge_id, review_date, review_stage)
                VALUES (?, ?, ?)
                """,
                knowledgeId,
                LocalDate.now().plusDays(days).toString(),
                stage);
    }

    private Map<String, Object> loadJsonPayload(String rawText) {
        String text = Objects.toString(rawText, "").trim();
        Matcher fenced = FENCED_JSON.matcher(text);
        if (fenced.find()) {
            text = fenced.group(1).trim();
        }
        if (!(text.startsWith("{") && text.endsWith("}"))) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new DailyReviewException("API 返回内容里没有可解析的 JSON。");
            }
            text = text.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(text, new TypeReference<>() {});
        } catch (JsonProcessingException first) {
            String escaped = text.replaceAll("\\\\(?![\"\\\\/bfnrtu])", "\\\\\\\\");
            try {
                return objectMapper.readValue(escaped, new TypeReference<>() {});
            } catch (JsonProcessingException second) {
                throw new DailyReviewException("API 返回内容不是合法 JSON 对象。", second);
            }
        }
    }

    private String topicForCandidate(List<ReviewCandidateDto> candidates, int knowledgeId) {
        for (ReviewCandidateDto candidate : candidates) {
            if (candidate.knowledgeId() == knowledgeId) {
                return defaultIfBlank(candidate.topic(), "未命名知识点");
            }
        }
        return "未命名知识点";
    }

    private String topicForPlan(DailyReviewPlanDto plan, int knowledgeId) {
        for (DailyReviewQuestionDto question : plan.questions()) {
            if (question.knowledgeId() == knowledgeId) {
                return defaultIfBlank(question.topic(), "未命名知识点");
            }
        }
        return "未命名知识点";
    }

    private String normalizeResult(Object value, int score) {
        String text = Objects.toString(value, "").trim();
        if (REVIEW_RESULTS.contains(text)) {
            return text;
        }
        if (score >= 85) {
            return "完全掌握";
        }
        if (score >= 65) {
            return "基本掌握";
        }
        if (score >= 40) {
            return "仍然模糊";
        }
        return "完全不会";
    }

    private String normalizeCause(Object value) {
        String text = Objects.toString(value, "").trim();
        return ERROR_CAUSE_CATEGORIES.contains(text) ? text : "概念不清";
    }

    private int applyReviewResult(int currentMastery, String result) {
        return clampMastery(currentMastery + switch (result) {
            case "完全掌握" -> 15;
            case "基本掌握" -> 5;
            case "仍然模糊" -> -5;
            case "完全不会" -> -15;
            default -> 0;
        });
    }

    private int queryKnowledgeMastery(int knowledgeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT mastery FROM knowledge_cards WHERE id = ?", knowledgeId);
        return rows.isEmpty() ? 0 : intValue(rows.get(0).get("mastery"), 0);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> Objects.toString(item, "").trim())
                .filter(item -> !item.isBlank())
                .toList();
    }

    private int clampInt(Object value, int defaultValue) {
        return clampMastery(intValue(value, defaultValue));
    }

    private int clampMastery(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String clip(String text, int limit) {
        String normalized = Objects.toString(text, "").replaceAll("\\s+", " ").trim();
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "...";
    }

    private Integer nullableInt(Object value) {
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value, "").trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exc) {
            return null;
        }
    }

    private int intValue(Object value, int defaultValue) {
        Integer parsed = nullableInt(value);
        return parsed == null ? defaultValue : parsed;
    }

    private String today() {
        return LocalDate.now().toString();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exc) {
            throw new DailyReviewException("JSON 序列化失败。", exc);
        }
    }

    private String writePretty(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exc) {
            throw new DailyReviewException("JSON 序列化失败。", exc);
        }
    }

    private Map<String, Object> readJsonMap(String text) {
        try {
            return objectMapper.readValue(text, new TypeReference<>() {});
        } catch (JsonProcessingException exc) {
            throw new DailyReviewException("JSON 解析失败。", exc);
        }
    }

    private <T> T readJson(String text, Class<T> type) {
        try {
            return objectMapper.readValue(text, type);
        } catch (JsonProcessingException exc) {
            throw new DailyReviewException("JSON 解析失败。", exc);
        }
    }

    private <T> List<T> readJsonList(String text, TypeReference<List<T>> typeReference) {
        try {
            return objectMapper.readValue(text, typeReference);
        } catch (JsonProcessingException exc) {
            return List.of();
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    public static class DailyReviewException extends RuntimeException {
        public DailyReviewException(String message) {
            super(message);
        }

        public DailyReviewException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
