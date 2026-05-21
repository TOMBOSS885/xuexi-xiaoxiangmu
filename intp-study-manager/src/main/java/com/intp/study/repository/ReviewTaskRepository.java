package com.intp.study.repository;

import com.intp.study.model.AppConstants;
import com.intp.study.model.ReviewTaskView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewTaskRepository {
    private final SqlRepository repo;

    public ReviewTaskRepository(SqlRepository repo) {
        this.repo = repo;
    }

    public long countByKnowledgeId(long knowledgeId) {
        return longValue(repo.scalar(
                "SELECT COUNT(*) AS count FROM review_tasks WHERE knowledge_id = ?",
                knowledgeId
        ));
    }

    public void insertInitialReviewTasks(
            long knowledgeId,
            LocalDate baseDate,
            List<AppConstants.ReviewInterval> intervals
    ) {
        List<Object[]> rows = new ArrayList<>(intervals.size());
        for (AppConstants.ReviewInterval interval : intervals) {
            rows.add(new Object[]{
                    knowledgeId,
                    baseDate.plusDays(interval.days()).toString(),
                    interval.stage()
            });
        }
        repo.jdbc().batchUpdate(
                """
                INSERT INTO review_tasks (knowledge_id, review_date, review_stage)
                VALUES (?, ?, ?)
                """,
                rows
        );
    }

    public List<ReviewTaskView> findReviewTasks(String whereClause, List<?> params) {
        String sql = """
                SELECT
                    rt.id,
                    rt.knowledge_id,
                    rt.review_date,
                    rt.review_stage,
                    rt.status,
                    rt.result,
                    kc.subject,
                    kc.topic,
                    kc.created_at AS original_learning_date,
                    kc.mastery,
                    (
                        SELECT m.cause_category
                        FROM mistakes m
                        WHERE m.knowledge_id = kc.id OR (m.subject = kc.subject AND m.topic = kc.topic)
                        ORDER BY m.created_at DESC
                        LIMIT 1
                    ) AS last_cause
                FROM review_tasks rt
                JOIN knowledge_cards kc ON kc.id = rt.knowledge_id
                """ + normalizeWhereClause(whereClause) + """
                ORDER BY rt.review_date ASC, rt.id ASC
                """;

        return repo.query(sql, params == null ? new Object[0] : params.toArray()).stream()
                .map(ReviewTaskRepository::toReviewTaskView)
                .toList();
    }

    public Optional<ReviewTaskWithMastery> findTaskWithMastery(long taskId) {
        return repo.queryOne(
                """
                SELECT rt.*, kc.mastery
                FROM review_tasks rt
                JOIN knowledge_cards kc ON kc.id = rt.knowledge_id
                WHERE rt.id = ?
                """,
                taskId
        ).map(row -> new ReviewTaskWithMastery(
                longValue(row.get("id")),
                longValue(row.get("knowledge_id")),
                intValue(row.get("mastery"))
        ));
    }

    public void markCompletedAndUpdateMastery(
            long taskId,
            String result,
            long knowledgeId,
            int newMastery
    ) {
        repo.update(
                "UPDATE review_tasks SET status = '已完成', result = ? WHERE id = ?",
                result,
                taskId
        );
        repo.update(
                "UPDATE knowledge_cards SET mastery = ? WHERE id = ?",
                newMastery,
                knowledgeId
        );
    }

    public long insertReviewTask(long knowledgeId, LocalDate reviewDate, String reviewStage) {
        return repo.insert(
                """
                INSERT INTO review_tasks (knowledge_id, review_date, review_stage)
                VALUES (?, ?, ?)
                """,
                knowledgeId,
                reviewDate.toString(),
                reviewStage
        );
    }

    private static ReviewTaskView toReviewTaskView(Map<String, Object> row) {
        return new ReviewTaskView(
                longValue(row.get("id")),
                longValue(row.get("knowledge_id")),
                stringValue(row.get("review_date")),
                stringValue(row.get("review_stage")),
                stringValue(row.get("status")),
                stringValue(row.get("result")),
                stringValue(row.get("subject")),
                stringValue(row.get("topic")),
                stringValue(row.get("original_learning_date")),
                intValue(row.get("mastery")),
                stringValue(row.get("last_cause"))
        );
    }

    private static String normalizeWhereClause(String whereClause) {
        if (whereClause == null || whereClause.isBlank()) {
            return "";
        }
        return "\n" + whereClause.strip() + "\n";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    public record ReviewTaskWithMastery(long taskId, long knowledgeId, int mastery) {
    }
}
