package com.intp.study.controller;

import com.intp.study.model.AppConstants;
import com.intp.study.repository.SqlRepository;
import com.intp.study.service.AiService;
import com.intp.study.service.PptService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PptTutorController {
    private final SqlRepository repo;
    private final PptService pptService;
    private final AiService aiService;

    public PptTutorController(SqlRepository repo, PptService pptService, AiService aiService) {
        this.repo = repo;
        this.pptService = pptService;
        this.aiService = aiService;
    }

    @GetMapping("/ppt-tutor")
    public String page(@RequestParam(required = false) Long deckId, Model model) {
        List<Map<String, Object>> decks = pptService.decks();
        Long activeDeckId = deckId != null ? deckId : decks.isEmpty() ? null : number(decks.get(0).get("id"));
        List<Map<String, Object>> slides = activeDeckId == null ? List.of() : enrichSlides(pptService.slides(activeDeckId));
        model.addAttribute("title", "PPT 逐页讲解");
        model.addAttribute("decks", decks);
        model.addAttribute("activeDeckId", activeDeckId);
        model.addAttribute("slides", slides);
        model.addAttribute("questions", activeDeckId == null ? List.of() : pptService.questions(activeDeckId));
        model.addAttribute("providers", aiService.listProviders(true));
        model.addAttribute("defaultModel", AppConstants.DEFAULT_MODEL);
        return "ppt-tutor";
    }

    @PostMapping("/ppt-tutor/upload")
    public String upload(@RequestParam MultipartFile file,
                         @RequestParam String subject,
                         @RequestParam String title,
                         RedirectAttributes redirect) {
        try {
            long deckId = pptService.importDeck(file, subject, title);
            redirect.addFlashAttribute("message", "资料已导入，编号 #" + deckId + "。");
            return "redirect:/ppt-tutor?deckId=" + deckId;
        } catch (RuntimeException exc) {
            redirect.addFlashAttribute("error", exc.getMessage());
            return "redirect:/ppt-tutor";
        }
    }

    @PostMapping("/ppt-tutor/explain")
    public String explain(@RequestParam long deckId,
                          @RequestParam long slideId,
                          @RequestParam long providerId,
                          @RequestParam(required = false) String apiKey,
                          @RequestParam(required = false) String model,
                          RedirectAttributes redirect) {
        try {
            Map<String, Object> deck = repo.queryOne("SELECT * FROM ppt_decks WHERE id = ?", deckId).orElseThrow();
            Map<String, Object> slide = repo.queryOne("SELECT * FROM ppt_slides WHERE id = ?", slideId).orElseThrow();
            String related = relatedKnowledge(text(deck.get("subject")));
            String prompt = pptService.buildSlidePrompt(deck, slide, related);
            String activeModel = text(model, AppConstants.DEFAULT_MODEL);
            String explanation = aiService.generateText(prompt, providerId, apiKey, activeModel, 4096);
            pptService.saveExplanation(slideId, "Provider #" + providerId + " / " + activeModel, explanation);
            redirect.addFlashAttribute("message", "第 " + slide.get("slide_number") + " 页讲解已生成。");
        } catch (RuntimeException exc) {
            redirect.addFlashAttribute("error", exc.getMessage());
        }
        return "redirect:/ppt-tutor?deckId=" + deckId;
    }

    @PostMapping("/ppt-tutor/question")
    public String question(@RequestParam long deckId,
                           @RequestParam long slideId,
                           @RequestParam long providerId,
                           @RequestParam(required = false) String apiKey,
                           @RequestParam(required = false) String model,
                           @RequestParam String question,
                           RedirectAttributes redirect) {
        try {
            Map<String, Object> deck = repo.queryOne("SELECT * FROM ppt_decks WHERE id = ?", deckId).orElseThrow();
            Map<String, Object> slide = repo.queryOne("SELECT * FROM ppt_slides WHERE id = ?", slideId).orElseThrow();
            String prompt = """
                    请基于下面资料页面回答我的插问，不要覆盖主线讲解。
                    资料：%s
                    页码：第 %s 页
                    页面文字：
                    %s

                    插问：%s
                    """.formatted(deck.get("title"), slide.get("slide_number"), slide.get("slide_text"), question);
            String activeModel = text(model, AppConstants.DEFAULT_MODEL);
            String answer = aiService.generateText(prompt, providerId, apiKey, activeModel, 1800);
            pptService.saveQuestion(slideId, question, answer, "Provider #" + providerId + " / " + activeModel);
            redirect.addFlashAttribute("message", "插问已回答并保存。");
        } catch (RuntimeException exc) {
            redirect.addFlashAttribute("error", exc.getMessage());
        }
        return "redirect:/ppt-tutor?deckId=" + deckId;
    }

    @GetMapping("/ppt-management")
    public String management(@RequestParam(required = false) Long deckId, Model model) {
        List<Map<String, Object>> decks = pptService.decks();
        Long activeDeckId = deckId != null ? deckId : decks.isEmpty() ? null : number(decks.get(0).get("id"));
        model.addAttribute("title", "PPT 与插问管理");
        model.addAttribute("decks", decks);
        model.addAttribute("activeDeckId", activeDeckId);
        model.addAttribute("questions", activeDeckId == null ? List.of() : pptService.questions(activeDeckId));
        return "ppt-management";
    }

    @PostMapping("/ppt-management/deck")
    public String updateDeck(@RequestParam long deckId,
                             @RequestParam String category,
                             @RequestParam int sortOrder,
                             @RequestParam String status,
                             RedirectAttributes redirect) {
        pptService.updateDeck(deckId, category, sortOrder, status);
        redirect.addFlashAttribute("message", "资料状态已更新。");
        return "redirect:/ppt-management?deckId=" + deckId;
    }

    private List<Map<String, Object>> enrichSlides(List<Map<String, Object>> slides) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> slide : slides) {
            Map<String, Object> item = new LinkedHashMap<>(slide);
            item.put("image_url", pptService.imageUrl(slide.get("image_path")));
            repo.queryOne("""
                    SELECT *
                    FROM slide_explanations
                    WHERE slide_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT 1
                    """, slide.get("id")).ifPresent(latest -> item.put("latest", latest));
            enriched.add(item);
        }
        return enriched;
    }

    private String relatedKnowledge(String subject) {
        if (subject.isBlank()) {
            return "暂无同科目知识卡片。";
        }
        List<Map<String, Object>> cards = repo.query("""
                SELECT id, topic, core_question, one_sentence, mastery
                FROM knowledge_cards
                WHERE subject = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 8
                """, subject);
        if (cards.isEmpty()) {
            return "暂无同科目知识卡片。";
        }
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> card : cards) {
            builder.append("- #").append(card.get("id")).append(" ")
                    .append(card.get("topic")).append("（掌握度 ").append(card.get("mastery")).append("%）：")
                    .append(card.get("core_question")).append("；").append(card.get("one_sentence")).append('\n');
        }
        return builder.toString();
    }

    private String text(Object value) {
        return text(value, "");
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).strip();
        return text.isBlank() ? fallback : text;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
