package com.intp.study.controller;

import com.intp.study.service.AiService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
public class ApiSettingsController {
    private final AiService aiService;

    public ApiSettingsController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/api-settings")
    public String page(Model model) {
        model.addAttribute("title", "API 接入设置");
        model.addAttribute("providers", aiService.listProviders(false));
        model.addAttribute("providerTypes", aiService.providerTypes());
        return "api-settings";
    }

    @PostMapping("/api-settings/provider")
    public String save(@RequestParam Map<String, String> form, RedirectAttributes redirect) {
        Long id = form.get("id") == null || form.get("id").isBlank() ? null : Long.parseLong(form.get("id"));
        long savedId = aiService.saveProvider(form, id);
        redirect.addFlashAttribute("message", "Provider 已保存：#" + savedId);
        return "redirect:/api-settings";
    }

    @PostMapping("/api-settings/test")
    public String test(@RequestParam long providerId,
                       @RequestParam(required = false) String apiKey,
                       @RequestParam(required = false) String model,
                       @RequestParam String prompt,
                       RedirectAttributes redirect) {
        try {
            String output = aiService.generateText(prompt, providerId, apiKey, model, 800);
            redirect.addFlashAttribute("message", "调用成功：" + output);
        } catch (RuntimeException exc) {
            redirect.addFlashAttribute("error", exc.getMessage());
        }
        return "redirect:/api-settings";
    }
}
