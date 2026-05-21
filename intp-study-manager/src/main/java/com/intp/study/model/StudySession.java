package com.intp.study.model;

public record StudySession(
        long id,
        String date,
        String subject,
        String chapter,
        String title,
        String mainQuestion,
        String masteredContent,
        String blockers,
        String wrongQuestions,
        String summary,
        int mastery,
        boolean needReview,
        boolean key,
        String createdAt
) {
}
