package com.intp.study.model;

public record KnowledgeCard(
        long id,
        String subject,
        String topic,
        String coreQuestion,
        String oneSentence,
        String logicOrFormula,
        String application,
        int mastery,
        boolean needReview,
        Long sourceSessionId,
        String createdAt
) {
}
