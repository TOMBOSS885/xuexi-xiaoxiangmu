package com.intp.study.model;

public final class StatsModels {
    private StatsModels() {
    }

    public record LowMasteryCard(
            long id,
            String subject,
            String topic,
            int mastery,
            String coreQuestion
    ) {
    }

    public record RecentBlocker(
            long id,
            String date,
            String subject,
            String title,
            String blockers,
            int mastery
    ) {
    }

    public record OpenParkingQuestion(
            long id,
            String subject,
            String question,
            String source,
            String status,
            String createdAt
    ) {
    }

    public record KnowledgeLinkView(
            String relationType,
            String relationNote,
            String comparePoints,
            String createdAt,
            String sourceSubject,
            String sourceTopic,
            String targetSubject,
            String targetTopic
    ) {
    }

    public record MistakeCauseCount(
            String causeCategory,
            long count
    ) {
    }
}
