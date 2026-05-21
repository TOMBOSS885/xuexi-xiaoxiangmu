package com.intp.study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intp.study.model.SaveStudyAssetsResult;
import com.intp.study.model.StudyAssets;
import com.intp.study.repository.StudyAssetRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyAssetService {
    private static final Pattern FENCED_JSON_PATTERN = Pattern.compile(
            "```(?:json)?\\s*(.*?)```",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final StudyAssetRepository studyAssetRepository;
    private final ReviewTaskService reviewTaskService;
    private final ObjectMapper objectMapper;

    public StudyAssetService(
            StudyAssetRepository studyAssetRepository,
            ReviewTaskService reviewTaskService,
            ObjectMapper objectMapper
    ) {
        this.studyAssetRepository = studyAssetRepository;
        this.reviewTaskService = reviewTaskService;
        this.objectMapper = objectMapper;
    }

    public StudyAssets parseStudyAssets(String rawText) {
        String jsonText = extractJsonText(rawText);
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(jsonText, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            try {
                payload = objectMapper.readValue(escapeLatexBackslashes(jsonText), MAP_TYPE);
            } catch (JsonProcessingException secondException) {
                throw new IllegalArgumentException("API 返回内容不是可解析的 JSON。", secondException);
            }
        }

        Object session = payload.get("study_session");
        Object cards = payload.get("knowledge_cards");
        if (!(session instanceof Map<?, ?> sessionMap)) {
            throw new IllegalArgumentException("JSON 缺少 study_session 对象。");
        }
        if (!(cards instanceof List<?> cardList)) {
            throw new IllegalArgumentException("JSON 缺少 knowledge_cards 数组。");
        }

        List<Map<String, Object>> normalizedCards = new ArrayList<>();
        for (Object card : cardList) {
            if (card instanceof Map<?, ?> cardMap) {
                normalizedCards.add(stringObjectMap(cardMap));
            }
        }
        if (normalizedCards.isEmpty()) {
            throw new IllegalArgumentException("knowledge_cards 为空，无法创建知识点卡片。");
        }
        return new StudyAssets(stringObjectMap(sessionMap), normalizedCards);
    }

    @Transactional
    public SaveStudyAssetsResult saveStudyAssets(
            StudyAssets assets,
            String fallbackSubject,
            String fallbackChapter
    ) {
        Map<String, Object> session = assets.studySession();
        String sessionDate = defaultString(text(session.get("date")), LocalDate.now().toString());
        String subject = defaultString(text(session.get("subject")), defaultString(fallbackSubject, "未分类"));
        String chapter = defaultString(text(session.get("chapter")), fallbackChapter);
        int mastery = clampInt(session.get("mastery"), 60);

        long sessionId = studyAssetRepository.insertStudySession(
                sessionDate,
                subject,
                chapter,
                defaultString(text(session.get("title")), chapter + " 阅读复盘"),
                defaultString(text(session.get("main_question")), "这份资料主要想解决什么问题？"),
                text(session.get("mastered_content")),
                text(session.get("blockers")),
                text(session.get("wrong_questions")),
                text(session.get("summary")),
                mastery,
                boolValue(session.get("need_review"), true),
                boolValue(session.get("is_key"), mastery < 70)
        );

        List<Long> knowledgeIds = new ArrayList<>();
        for (Map<String, Object> card : assets.knowledgeCards()) {
            int cardMastery = clampInt(card.get("mastery"), mastery);
            boolean needReview = boolValue(card.get("need_review"), true);
            long knowledgeId = studyAssetRepository.insertKnowledgeCard(
                    defaultString(text(card.get("subject")), subject),
                    defaultString(text(card.get("topic")), "未命名知识点"),
                    text(card.get("core_question")),
                    defaultString(text(card.get("one_sentence")), "待补充一句话解释"),
                    text(card.get("logic_or_formula")),
                    text(card.get("application")),
                    cardMastery,
                    needReview,
                    sessionId
            );
            if (needReview) {
                reviewTaskService.ensureInitialReviewTasks(knowledgeId, sessionDate);
            }
            knowledgeIds.add(knowledgeId);
        }

        return new SaveStudyAssetsResult(sessionId, knowledgeIds);
    }

    private static String extractJsonText(String rawText) {
        String text = Objects.requireNonNull(rawText, "rawText").strip();
        Matcher matcher = FENCED_JSON_PATTERN.matcher(text);
        if (matcher.find()) {
            text = matcher.group(1).strip();
        }
        if (text.startsWith("{") && text.endsWith("}")) {
            return text;
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new IllegalArgumentException("API 返回内容里没有可解析的 JSON。");
        }
        return text.substring(start, end + 1);
    }

    private static String escapeLatexBackslashes(String jsonText) {
        StringBuilder escaped = new StringBuilder(jsonText.length());
        String allowed = "\"\\/bfnrtu";
        for (int index = 0; index < jsonText.length(); index++) {
            char c = jsonText.charAt(index);
            if (c == '\\' && (index + 1 >= jsonText.length() || allowed.indexOf(jsonText.charAt(index + 1)) == -1)) {
                escaped.append("\\\\");
            } else {
                escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }

    private static String defaultString(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue == null ? "" : defaultValue;
        }
        return value;
    }

    private static int clampInt(Object value, int defaultValue) {
        int number = defaultValue;
        if (value instanceof Number numericValue) {
            number = numericValue.intValue();
        } else {
            String textValue = text(value);
            if (!textValue.isBlank()) {
                try {
                    number = Integer.parseInt(textValue);
                } catch (NumberFormatException ignored) {
                    number = defaultValue;
                }
            }
        }
        return Math.max(0, Math.min(100, number));
    }

    private static boolean boolValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numericValue) {
            return numericValue.doubleValue() != 0.0d;
        }
        String textValue = text(value).toLowerCase(Locale.ROOT);
        if (List.of("true", "1", "yes", "y", "需要", "是").contains(textValue)) {
            return true;
        }
        if (List.of("false", "0", "no", "n", "不需要", "否").contains(textValue)) {
            return false;
        }
        return defaultValue;
    }
}
