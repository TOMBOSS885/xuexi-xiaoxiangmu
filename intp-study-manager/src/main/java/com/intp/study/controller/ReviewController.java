package com.intp.study.controller;

import com.intp.study.model.AppConstants;
import com.intp.study.service.ReviewTaskService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReviewController {
    private final ReviewTaskService reviewTaskService;

    public ReviewController(ReviewTaskService reviewTaskService) {
        this.reviewTaskService = reviewTaskService;
    }

    @GetMapping("/reviews")
    public String page(Model model) {
        model.addAttribute("title", "复习计划");
        model.addAttribute("todayTasks", reviewTaskService.todayTasks());
        model.addAttribute("pendingTasks", reviewTaskService.pendingTasks());
        model.addAttribute("reviewResults", AppConstants.REVIEW_RESULTS);
        return "reviews";
    }

    @PostMapping("/reviews/mark")
    public String mark(@RequestParam long taskId, @RequestParam String result, RedirectAttributes redirect) {
        reviewTaskService.markReviewResult(taskId, result);
        redirect.addFlashAttribute("message", "复习结果已记录，掌握度已更新。");
        return "redirect:/reviews";
    }
}
