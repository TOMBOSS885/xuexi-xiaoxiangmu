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
public class KnowledgeCardController {
    private final SqlRepository repo;
    private final ReviewTaskService reviewTaskService;

    public KnowledgeCardController(SqlRepository repo, ReviewTaskService reviewTaskService) {
        this.repo = repo;
        this.reviewTaskService = reviewTaskService;
    }

    @GetMapping("/knowledge-cards")
    public String page(Model model) {
        model.addAttribute("title", "知识点卡片");
        model.addAttribute("cards", repo.query("SELECT * FROM knowledge_cards ORDER BY created_at DESC, id DESC"));
        model.addAttribute("sessions", repo.query("SELECT id, date, subject, title FROM study_sessions ORDER BY date DESC, id DESC"));
        model.addAttribute("links", repo.query("""
                SELECT kl.*, s.topic AS source_topic, t.topic AS target_topic
                FROM knowledge_links kl
                JOIN knowledge_cards s ON s.id = kl.source_knowledge_id
                JOIN knowledge_cards t ON t.id = kl.target_knowledge_id
                ORDER BY kl.created_at DESC, kl.id DESC
                """));
        return "knowledge-cards";
    }

    @PostMapping("/knowledge-cards")
    public String create(@RequestParam Map<String, String> form, RedirectAttributes redirect) {
        if (blank(form.get("subject")) || blank(form.get("topic")) || blank(form.get("oneSentence"))) {
            redirect.addFlashAttribute("error", "科目、知识点、一句话解释不能为空。");
            return "redirect:/knowledge-cards";
        }
        Long sourceSessionId = blank(form.get("sourceSessionId")) ? null : Long.parseLong(form.get("sourceSessionId"));
        long cardId = repo.insert("""
                INSERT INTO knowledge_cards (
                    subject, topic, core_question, one_sentence, logic_or_formula,
                    application, mastery, need_review, source_session_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                text(form.get("subject")), text(form.get("topic")), text(form.get("coreQuestion")),
                text(form.get("oneSentence")), text(form.get("logicOrFormula")), text(form.get("application")),
                intValue(form.get("mastery"), 60), checked(form, "needReview"), sourceSessionId);
        if (checked(form, "needReview") == 1) {
            reviewTaskService.ensureInitialReviewTasks(cardId, LocalDate.now());
        }
        redirect.addFlashAttribute("message", "知识点卡片已保存。");
        return "redirect:/knowledge-cards";
    }

    @PostMapping("/knowledge-links")
    public String link(@RequestParam Map<String, String> form, RedirectAttributes redirect) {
        if (form.get("sourceKnowledgeId").equals(form.get("targetKnowledgeId"))) {
            redirect.addFlashAttribute("error", "不能把知识点连接到自身。");
            return "redirect:/knowledge-cards";
        }
        repo.insert("""
                INSERT OR REPLACE INTO knowledge_links (
                    source_knowledge_id, target_knowledge_id, relation_type, relation_note, compare_points
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                Long.parseLong(form.get("sourceKnowledgeId")),
                Long.parseLong(form.get("targetKnowledgeId")),
                text(form.get("relationType"), "关联"),
                text(form.get("relationNote")),
                text(form.get("comparePoints")));
        redirect.addFlashAttribute("message", "知识链接已建立。");
        return "redirect:/knowledge-cards";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String text(String value) {
        return text(value, "");
    }

    private String text(String value, String fallback) {
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
