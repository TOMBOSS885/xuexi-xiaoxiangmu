package com.intp.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DailyReviewEvaluationDto(
        @JsonProperty("overall_summary") String overallSummary,
        List<DailyReviewEvaluationItemDto> evaluations,
        @JsonProperty("mastery_updates") List<MasteryUpdateDto> masteryUpdates
) {
    public DailyReviewEvaluationDto {
        overallSummary = overallSummary == null ? "" : overallSummary;
        evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
        masteryUpdates = masteryUpdates == null ? List.of() : List.copyOf(masteryUpdates);
    }
}
