package com.intp.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewCandidateDto(
        @JsonProperty("knowledge_id") int knowledgeId,
        @JsonProperty("task_id") Integer taskId,
        String subject,
        String topic,
        @JsonProperty("core_question") String coreQuestion,
        @JsonProperty("one_sentence") String oneSentence,
        @JsonProperty("logic_or_formula") String logicOrFormula,
        String application,
        int mastery,
        @JsonProperty("review_stage") String reviewStage,
        @JsonProperty("last_cause") String lastCause
) {
    public ReviewCandidateDto {
        subject = valueOrBlank(subject);
        topic = valueOrBlank(topic);
        coreQuestion = valueOrBlank(coreQuestion);
        oneSentence = valueOrBlank(oneSentence);
        logicOrFormula = valueOrBlank(logicOrFormula);
        application = valueOrBlank(application);
        reviewStage = valueOrBlank(reviewStage);
        lastCause = valueOrBlank(lastCause);
    }

    private static String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
