package com.intp.study.repository;

import com.intp.study.model.StatsModels.KnowledgeLinkView;
import com.intp.study.model.StatsModels.LowMasteryCard;
import com.intp.study.model.StatsModels.MistakeCauseCount;
import com.intp.study.model.StatsModels.OpenParkingQuestion;
import com.intp.study.model.StatsModels.RecentBlocker;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class StatsRepository {
    private static final Set<String> COUNTABLE_TABLES = Set.of(
            "study_sessions",
            "mainline_anchors",
            "branch_questions",
            "knowledge_cards",
            "knowledge_links",
            "mistakes",
            "review_tasks",
            "parking_lot",
            "ppt_decks",
            "ppt_slides",
            "slide_explanations",
            "slide_questions",
            "api_providers",
            "app_settings",
            "daily_review_logs",
            "daily_ai_review_plans"
    );

    private final SqlRepository repo;

    public StatsRepository(SqlRepository repo) {
        this.repo = repo;
    }

    public long count(String table) {
        if (!COUNTABLE_TABLES.contains(table)) {
            throw new IllegalArgumentException("不允许统计未知数据表：" + table);
        }
        return longValue(repo.scalar("SELECT COUNT(*) FROM " + table));
    }

    public List<LowMasteryCard> lowMasteryCards(int limit) {
        return repo.query(
                """
                SELECT id, subject, topic, mastery, core_question
                FROM knowledge_cards
                WHERE mastery < 70
                ORDER BY mastery ASC, created_at DESC
                LIMIT ?
                """,
                limit
        ).stream().map(row -> new LowMasteryCard(
                longValue(row.get("id")),
                stringValue(row.get("subject")),
                stringValue(row.get("topic")),
                intValue(row.get("mastery")),
                stringValue(row.get("core_question"))
        )).toList();
    }

    public List<RecentBlocker> recentBlockers(int limit) {
        return repo.query(
                """
                SELECT id, date, subject, title, blockers, mastery
                FROM study_sessions
                WHERE TRIM(blockers) != ''
                ORDER BY date DESC, id DESC
                LIMIT ?
                """,
                limit
        ).stream().map(row -> new RecentBlocker(
                longValue(row.get("id")),
                stringValue(row.get("date")),
                stringValue(row.get("subject")),
                stringValue(row.get("title")),
                stringValue(row.get("blockers")),
                intValue(row.get("mastery"))
        )).toList();
    }

    public List<OpenParkingQuestion> openParkingQuestions(String resolvedStatus, int limit) {
        return repo.query(
                """
                SELECT id, subject, question, source, status, created_at
                FROM parking_lot
                WHERE status != ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                resolvedStatus,
                limit
        ).stream().map(row -> new OpenParkingQuestion(
                longValue(row.get("id")),
                stringValue(row.get("subject")),
                stringValue(row.get("question")),
                stringValue(row.get("source")),
                stringValue(row.get("status")),
                stringValue(row.get("created_at"))
        )).toList();
    }

    public List<KnowledgeLinkView> recentKnowledgeLinks(int limit) {
        return repo.query(
                """
                SELECT
                    kl.relation_type,
                    kl.relation_note,
                    kl.compare_points,
                    kl.created_at,
                    source.subject AS source_subject,
                    source.topic AS source_topic,
                    target.subject AS target_subject,
                    target.topic AS target_topic
                FROM knowledge_links kl
                JOIN knowledge_cards source ON source.id = kl.source_knowledge_id
                JOIN knowledge_cards target ON target.id = kl.target_knowledge_id
                ORDER BY kl.created_at DESC, kl.id DESC
                LIMIT ?
                """,
                limit
        ).stream().map(row -> new KnowledgeLinkView(
                stringValue(row.get("relation_type")),
                stringValue(row.get("relation_note")),
                stringValue(row.get("compare_points")),
                stringValue(row.get("created_at")),
                stringValue(row.get("source_subject")),
                stringValue(row.get("source_topic")),
                stringValue(row.get("target_subject")),
                stringValue(row.get("target_topic"))
        )).toList();
    }

    public List<MistakeCauseCount> mistakeCauseCounts(String subject) {
        if (subject == null || subject.isBlank()) {
            return repo.query(
                    """
                    SELECT cause_category, COUNT(*) AS count
                    FROM mistakes
                    GROUP BY cause_category
                    ORDER BY count DESC, cause_category ASC
                    """
            ).stream().map(StatsRepository::toMistakeCauseCount).toList();
        }
        return repo.query(
                """
                SELECT cause_category, COUNT(*) AS count
                FROM mistakes
                WHERE subject = ?
                GROUP BY cause_category
                ORDER BY count DESC, cause_category ASC
                """,
                subject
        ).stream().map(StatsRepository::toMistakeCauseCount).toList();
    }

    private static MistakeCauseCount toMistakeCauseCount(Map<String, Object> row) {
        return new MistakeCauseCount(
                stringValue(row.get("cause_category")),
                longValue(row.get("count"))
        );
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
}
