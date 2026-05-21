package com.intp.study.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StudyAssets(
        Map<String, Object> studySession,
        List<Map<String, Object>> knowledgeCards
) {
    public StudyAssets {
        if (studySession == null) {
            throw new IllegalArgumentException("studySession must not be null");
        }
        if (knowledgeCards == null) {
            throw new IllegalArgumentException("knowledgeCards must not be null");
        }

        studySession = Collections.unmodifiableMap(new LinkedHashMap<>(studySession));
        List<Map<String, Object>> copiedCards = new ArrayList<>(knowledgeCards.size());
        for (Map<String, Object> card : knowledgeCards) {
            copiedCards.add(Collections.unmodifiableMap(new LinkedHashMap<>(card)));
        }
        knowledgeCards = Collections.unmodifiableList(copiedCards);
    }
}
