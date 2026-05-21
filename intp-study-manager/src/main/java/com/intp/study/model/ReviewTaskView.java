package com.intp.study.model;

public record ReviewTaskView(
        long id,
        long knowledgeId,
        String reviewDate,
        String reviewStage,
        String status,
        String result,
        String subject,
        String topic,
        String originalLearningDate,
        int mastery,
        String lastCause
) {
}
