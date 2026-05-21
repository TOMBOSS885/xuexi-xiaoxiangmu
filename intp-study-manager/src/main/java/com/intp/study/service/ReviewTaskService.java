package com.intp.study.service;

import com.intp.study.model.AppConstants;
import com.intp.study.model.ReviewTaskView;
import com.intp.study.model.StudyConstants;
import com.intp.study.repository.ReviewTaskRepository;
import com.intp.study.repository.ReviewTaskRepository.ReviewTaskWithMastery;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewTaskService {
    private final ReviewTaskRepository reviewTaskRepository;
    private final MasteryService masteryService;

    public ReviewTaskService(ReviewTaskRepository reviewTaskRepository, MasteryService masteryService) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.masteryService = masteryService;
    }

    public void createInitialReviewTasks(long knowledgeId) {
        createInitialReviewTasks(knowledgeId, LocalDate.now());
    }

    public void createInitialReviewTasks(long knowledgeId, String startDate) {
        createInitialReviewTasks(knowledgeId, toDate(startDate));
    }

    public void createInitialReviewTasks(long knowledgeId, LocalDate startDate) {
        reviewTaskRepository.insertInitialReviewTasks(
                knowledgeId,
                startDate == null ? LocalDate.now() : startDate,
                AppConstants.REVIEW_INTERVALS
        );
    }

    public void ensureInitialReviewTasks(long knowledgeId) {
        ensureInitialReviewTasks(knowledgeId, LocalDate.now());
    }

    public void ensureInitialReviewTasks(long knowledgeId, String startDate) {
        ensureInitialReviewTasks(knowledgeId, toDate(startDate));
    }

    public void ensureInitialReviewTasks(long knowledgeId, LocalDate startDate) {
        if (reviewTaskRepository.countByKnowledgeId(knowledgeId) == 0) {
            createInitialReviewTasks(knowledgeId, startDate);
        }
    }

    public List<ReviewTaskView> getReviewTasks() {
        return getReviewTasks("", List.of());
    }

    public List<ReviewTaskView> getReviewTasks(String whereClause, List<?> params) {
        return reviewTaskRepository.findReviewTasks(whereClause, params == null ? List.of() : params);
    }

    public List<ReviewTaskView> getTodayReviewTasks() {
        return getReviewTasks(
                "WHERE rt.review_date <= ? AND rt.status = ?",
                List.of(LocalDate.now().toString(), StudyConstants.STATUS_PENDING_REVIEW)
        );
    }

    public List<ReviewTaskView> todayTasks() {
        return getTodayReviewTasks();
    }

    public List<ReviewTaskView> getAllPendingReviewTasks() {
        return getReviewTasks(
                "WHERE rt.status = ?",
                List.of(StudyConstants.STATUS_PENDING_REVIEW)
        );
    }

    public List<ReviewTaskView> pendingTasks() {
        return getAllPendingReviewTasks();
    }

    @Transactional
    public void markReviewResult(long taskId, String result) {
        ReviewTaskWithMastery task = reviewTaskRepository.findTaskWithMastery(taskId).orElse(null);
        if (task == null) {
            return;
        }

        int newMastery = masteryService.applyReviewResult(task.mastery(), result);
        reviewTaskRepository.markCompletedAndUpdateMastery(
                task.taskId(),
                result,
                task.knowledgeId(),
                newMastery
        );

        if ("仍然模糊".equals(result)) {
            createExtraReview(task.knowledgeId(), 2, "追加复习：2 天后");
        } else if ("完全不会".equals(result)) {
            createExtraReview(task.knowledgeId(), 1, "重点突破：1 天后");
        }
    }

    private void createExtraReview(long knowledgeId, int days, String stage) {
        reviewTaskRepository.insertReviewTask(
                knowledgeId,
                LocalDate.now().plusDays(days),
                stage
        );
    }

    private static LocalDate toDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        String text = value.strip();
        return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
    }
}
