package com.intp.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DailyReviewEvaluationItemDto(
        @JsonProperty("question_id") String questionId,
        @JsonProperty("knowledge_id") int knowledgeId,
        int score,
        String result,
        @JsonProperty("cause_category") String causeCategory,
        String feedback,
        @JsonProperty("correct_answer") String correctAnswer,
        @JsonProperty("next_question") String nextQuestion
) {
    public DailyReviewEvaluationItemDto {
        questionId = valueOrBlank(questionId);
        result = valueOrBlank(result);
        causeCategory = valueOrBlank(causeCategory);
        feedback = valueOrBlank(feedback);
        correctAnswer = valueOrBlank(correctAnswer);
        nextQuestion = valueOrBlank(nextQuestion);
    }

    private static String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
