package com.intp.study.model;

public record ReviewTask(
        long id,
        long knowledgeId,
        String reviewDate,
        String reviewStage,
        String status,
        String result,
        String createdAt
) {
}
