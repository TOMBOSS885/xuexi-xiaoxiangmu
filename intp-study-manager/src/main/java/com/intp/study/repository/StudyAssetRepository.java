package com.intp.study.repository;

import org.springframework.stereotype.Repository;

@Repository
public class StudyAssetRepository {
    private final SqlRepository repo;

    public StudyAssetRepository(SqlRepository repo) {
        this.repo = repo;
    }

    public long insertStudySession(
            String sessionDate,
            String subject,
            String chapter,
            String title,
            String mainQuestion,
            String masteredContent,
            String blockers,
            String wrongQuestions,
            String summary,
            int mastery,
            boolean needReview,
            boolean key
    ) {
        return repo.insert(
                """
                INSERT INTO study_sessions (
                    date, subject, chapter, title, main_question, mastered_content,
                    blockers, wrong_questions, summary, mastery, need_review, is_key
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sessionDate,
                subject,
                chapter,
                title,
                mainQuestion,
                masteredContent,
                blockers,
                wrongQuestions,
                summary,
                mastery,
                needReview ? 1 : 0,
                key ? 1 : 0
        );
    }

    public long insertKnowledgeCard(
            String subject,
            String topic,
            String coreQuestion,
            String oneSentence,
            String logicOrFormula,
            String application,
            int mastery,
            boolean needReview,
            long sourceSessionId
    ) {
        return repo.insert(
                """
                INSERT INTO knowledge_cards (
                    subject, topic, core_question, one_sentence, logic_or_formula,
                    application, mastery, need_review, source_session_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                subject,
                topic,
                coreQuestion,
                oneSentence,
                logicOrFormula,
                application,
                mastery,
                needReview ? 1 : 0,
                sourceSessionId
        );
    }
}
