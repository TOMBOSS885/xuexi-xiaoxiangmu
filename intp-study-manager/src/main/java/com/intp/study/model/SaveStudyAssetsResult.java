package com.intp.study.model;

import java.util.List;

public record SaveStudyAssetsResult(long sessionId, List<Long> knowledgeIds) {
    public SaveStudyAssetsResult {
        knowledgeIds = List.copyOf(knowledgeIds);
    }
}
