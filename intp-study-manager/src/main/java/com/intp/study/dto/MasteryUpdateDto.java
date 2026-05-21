package com.intp.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MasteryUpdateDto(
        @JsonProperty("knowledge_id") int knowledgeId,
        String topic,
        int score,
        String result,
        @JsonProperty("mastery_before") int masteryBefore,
        @JsonProperty("mastery_after") int masteryAfter
) {
    public MasteryUpdateDto {
        topic = topic == null ? "" : topic;
        result = result == null ? "" : result;
    }
}
