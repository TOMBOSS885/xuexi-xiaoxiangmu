package com.intp.study.controller;

import com.intp.study.model.AppConstants;
import com.intp.study.repository.SqlRepository;
import com.intp.study.service.AppSettingService;
import com.intp.study.service.ReviewTaskService;
import com.intp.study.service.StatsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Map;

@Controller
public class MiscController {
    private final SqlRepository repo;
    private final ReviewTaskService reviewTaskService;
    private final StatsService statsService;
    private final AppSettingService appSettingService;

    public MiscController(SqlRepository repo, ReviewTaskService reviewTaskService, StatsService statsService, AppSettingService appSettingService) {
        this.repo = repo;
        this.reviewTaskService = reviewTaskService;
        this.statsService = statsService;
        this.appSettingService = appSettingService;
    }

    @GetMapping("/mistakes")
    public String mistakes(Model model) {
        model.addAttribute("title", "错因本");
        model.addAttribute("cards", repo.query("SELECT id, subject, topic FROM knowledge_cards ORDER BY created_at DESC, id DESC"));
        model.addAttribute("mistakes", repo.query("SELECT * FROM mistakes ORDER BY created_at DESC, id DESC"));
        model.addAttribute("causeCategories", AppConstants.ERROR_CAUSE_CATEGORIES);
        model.addAttribute("causeCounts", statsService.mistakeCauseCounts(null));
        return "mistakes";
    }

    @PostMapping("/mistakes")
    public String createMistake(@RequestParam Map<String, String> form, RedirectAttributes redirect) {
        Long knowledgeId = blank(form.get("knowledgeId")) ? null : Long.parseLong(form.get("knowledgeId"));
        repo.insert("""
                INSERT INTO mistakes (
                    subject, topic, knowledge_id, original_question, my_wrong_answer,
                    correct_idea, cause_category, warning_signal, summary, add_to_review
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                text(form.get("subject")), text(form.get("topic")), knowledgeId,
                text(form.get("originalQuestion")), text(form.get("myWrongAnswer")),
                text(form.get("correctIdea")), text(form.get("causeCategory"), "概念不清"),
                text(form.get("warningSignal")), text(form.get("summary")), checked(form, "addToReview"));
        if (knowledgeId != null && checked(form, "addToReview") == 1) {
            reviewTaskService.ensureInitialReviewTasks(knowledgeId, LocalDate.now());
        }
        redirect.addFlashAttribute("message", "错因记录已保存。");
        return "redirect:/mistakes";
    }

    @GetMapping("/parking-lot")
    public String parking(Model model) {
        model.addAttribute("title", "探索停车场");
        model.addAttribute("items", repo.query("SELECT * FROM parking_lot ORDER BY created_at DESC, id DESC"));
        return "parking-lot";
    }

    @PostMapping("/parking-lot")
    public String createParking(@RequestParam Map<String, String> form, RedirectAttributes redirect) {
        repo.insert("""
                INSERT INTO parking_lot (subject, question, source, status)
                VALUES (?, ?, ?, ?)
                """, text(form.get("subject")), text(form.get("question")), text(form.get("source")), text(form.get("status"), "未解决"));
        redirect.addFlashAttribute("message", "停车场问题已保存。");
        return "redirect:/parking-lot";
    }

    @PostMapping("/parking-lot/status")
    public String updateParking(@RequestParam long id, @RequestParam String status, RedirectAttributes redirect) {
        repo.update("UPDATE parking_lot SET status = ? WHERE id = ?", status, id);
        redirect.addFlashAttribute("message", "状态已更新。");
        return "redirect:/parking-lot";
    }

    @GetMapping("/mainline-branches")
    public String mainline(Model model) {
        model.addAttribute("title", "主线与插问");
        model.addAttribute("sessions", repo.query("SELECT * FROM study_sessions ORDER BY date DESC, id DESC"));
        model.addAttribute("anchors", repo.query("""
                SELECT ma.*, ss.date, ss.subject, ss.title AS session_title
                FROM mainline_anchors ma
                JOIN study_sessions ss ON ss.id = ma.session_id
                ORDER BY ss.date DESC, ma.order_index ASC, ma.id ASC
                """));
        model.addAttribute("branches", repo.query("""
                SELECT bq.*, ma.anchor_code, ma.title AS anchor_title, ss.subject, ss.title AS session_title
                FROM branch_questions bq
                JOIN mainline_anchors ma ON ma.id = bq.anchor_id
                JOIN study_sessions ss ON ss.id = bq.session_id
                ORDER BY bq.created_at DESC, bq.id DESC
                """));
        return "mainline-branches";
    }

    @PostMapping("/mainline-anchors")
    public String createAnchor(@RequestParam Map<String, String> form, RedirectAttributes redirect) {
        repo.insert("""
                INSERT INTO mainline_anchors (session_id, anchor_code, title, content, order_index)
                VALUES (?, ?, ?, ?, ?)
                """, Long.parseLong(form.get("sessionId")), text(form.get("anchorCode")), text(form.get("title")),
                text(form.get("content")), intValue(form.get("orderIndex"), 0));
        redirect.addFlashAttribute("message", "主线锚点已保存。");
        return "redirect:/mainline-branches";
    }

    @PostMapping("/branch-questions")
    public String createBranch(@RequestParam Map<String, String> form, RedirectAttributes redirect) {
        Map<String, Object> anchor = repo.queryOne("SELECT * FROM mainline_anchors WHERE id = ?", Long.parseLong(form.get("anchorId"))).orElseThrow();
        repo.insert("""
                INSERT INTO branch_questions (session_id, anchor_id, question, answer_summary, understood, need_review)
                VALUES (?, ?, ?, ?, ?, ?)
                """, anchor.get("session_id"), anchor.get("id"), text(form.get("question")), text(form.get("answerSummary")),
                checked(form, "understood"), checked(form, "needReview"));
        redirect.addFlashAttribute("message", "插问已绑定到主线锚点。");
        return "redirect:/mainline-branches";
    }

    @GetMapping("/reminders")
    public String reminders(Model model) {
        model.addAttribute("title", "每日复盘提醒");
        model.addAttribute("enabled", appSettingService.get("daily_review_reminder_enabled", "0"));
        model.addAttribute("time", appSettingService.get("daily_review_reminder_time", "20:30"));
        model.addAttribute("todayLog", repo.queryOne("SELECT * FROM daily_review_logs WHERE review_date = ?", LocalDate.now().toString()).orElse(null));
        return "reminders";
    }

    @PostMapping("/reminders/config")
    public String saveReminder(@RequestParam Map<String, String> form, RedirectAttributes redirect) {
        appSettingService.set("daily_review_reminder_enabled", String.valueOf(checked(form, "enabled")));
        appSettingService.set("daily_review_reminder_time", text(form.get("time"), "20:30"));
        redirect.addFlashAttribute("message", "提醒配置已保存。Spring Scheduling 会在应用运行期间检查配置。");
        return "redirect:/reminders";
    }

    @PostMapping("/reminders/done")
    public String markReviewDone(@RequestParam(required = false) String notes, RedirectAttributes redirect) {
        repo.update("""
                INSERT INTO daily_review_logs (review_date, status, notes)
                VALUES (?, '已完成', ?)
                ON CONFLICT(review_date) DO UPDATE SET
                    status = '已完成',
                    notes = excluded.notes,
                    created_at = datetime('now', 'localtime')
                """, LocalDate.now().toString(), text(notes));
        redirect.addFlashAttribute("message", "今日复盘已标记完成。");
        return "redirect:/reminders";
    }

    @GetMapping("/quiz-prompts")
    public String quizPrompts(Model model) {
        model.addAttribute("title", "闭卷测试 Prompt");
        model.addAttribute("records", repo.query("SELECT * FROM study_sessions ORDER BY date DESC, id DESC LIMIT 30"));
        model.addAttribute("cards", repo.query("SELECT * FROM knowledge_cards ORDER BY created_at DESC, id DESC LIMIT 60"));
        return "quiz-prompts";
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
