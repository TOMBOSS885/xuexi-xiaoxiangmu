package com.intp.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DailyReviewQuestionDto(
        @JsonProperty("question_id") String questionId,
        @JsonProperty("knowledge_id") int knowledgeId,
        String topic,
        @JsonProperty("question_type") String questionType,
        String question,
        @JsonProperty("expected_points") List<String> expectedPoints
) {
    public DailyReviewQuestionDto {
        questionId = valueOrBlank(questionId);
        topic = valueOrBlank(topic);
        questionType = valueOrBlank(questionType);
        question = valueOrBlank(question);
        expectedPoints = expectedPoints == null ? List.of() : List.copyOf(expectedPoints);
    }

    private static String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
