package com.intp.study.controller;

import com.intp.study.repository.SqlRepository;
import com.intp.study.service.ReviewTaskService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Map;

@Controller
public class StudySessionController {
    private final SqlRepository repo;
    private final ReviewTaskService reviewTaskService;

    public StudySessionController(SqlRepository repo, ReviewTaskService reviewTaskService) {
        this.repo = repo;
        this.reviewTaskService = reviewTaskService;
    }

    @GetMapping("/study-sessions")
    public String page(Model model) {
        model.addAttribute("title", "学习登记");
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("records", repo.query("SELECT * FROM study_sessions ORDER BY date DESC, id DESC"));
        return "study-sessions";
    }

    @PostMapping("/study-sessions")
    public String create(@RequestParam Map<String, String> form, RedirectAttributes redirect) {
        if (blank(form.get("subject")) || blank(form.get("title")) || blank(form.get("mainQuestion"))) {
            redirect.addFlashAttribute("error", "科目、今日学习主题、核心问题不能为空。");
            return "redirect:/study-sessions";
        }
        long sessionId = repo.insert("""
                INSERT INTO study_sessions (
                    date, subject, chapter, title, main_question, mastered_content,
                    blockers, wrong_questions, summary, mastery, need_review, is_key
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                value(form, "date", LocalDate.now().toString()),
                value(form, "subject"),
                value(form, "chapter"),
                value(form, "title"),
                value(form, "mainQuestion"),
                value(form, "masteredContent"),
                value(form, "blockers"),
                value(form, "wrongQuestions"),
                value(form, "summary"),
                intValue(form.get("mastery"), 60),
                checked(form, "needReview"),
                checked(form, "isKey"));
        if (checked(form, "createCard") == 1) {
            long cardId = repo.insert("""
                    INSERT INTO knowledge_cards (
                        subject, topic, core_question, one_sentence, logic_or_formula,
                        application, mastery, need_review, source_session_id
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    value(form, "subject"),
                    value(form, "title"),
                    value(form, "mainQuestion"),
                    value(form, "masteredContent", "待补充一句话解释"),
                    value(form, "summary"),
                    value(form, "wrongQuestions"),
                    intValue(form.get("mastery"), 60),
                    checked(form, "needReview"),
                    sessionId);
            if (checked(form, "needReview") == 1) {
                reviewTaskService.createInitialReviewTasks(cardId, LocalDate.parse(value(form, "date", LocalDate.now().toString())));
            }
        }
        redirect.addFlashAttribute("message", "学习记录已保存。");
        return "redirect:/study-sessions";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String value(Map<String, String> form, String key) {
        return value(form, key, "");
    }

    private String value(Map<String, String> form, String key, String fallback) {
        String value = form.get(key);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private int checked(Map<String, String> form, String key) {
        return form.containsKey(key) ? 1 : 0;
    }

    private int intValue(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception exc) {
            return fallback;
        }
    }
}
