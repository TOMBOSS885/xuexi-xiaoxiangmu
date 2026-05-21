package com.intp.study.service;

import com.intp.study.model.AppConstants;
import org.springframework.stereotype.Service;

@Service
public class MasteryService {
    public int clampMastery(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public int applyReviewResult(int currentMastery, String result) {
        int change = AppConstants.REVIEW_CHANGES.getOrDefault(result, 0);
        return clampMastery(currentMastery + change);
    }
}
