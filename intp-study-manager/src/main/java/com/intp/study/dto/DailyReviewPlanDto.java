package com.intp.study.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DailyReviewPlanDto(
        @JsonProperty("main_line") String mainLine,
        List<DailyReviewQuestionDto> questions
) {
    public DailyReviewPlanDto {
        mainLine = mainLine == null ? "" : mainLine;
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
