package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.dashboard.DashboardPortfolioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private final DashboardPortfolioService dashboardPortfolioService;

    public DashboardController(DashboardPortfolioService dashboardPortfolioService) {
        this.dashboardPortfolioService = dashboardPortfolioService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) Long sellerId,
                            @RequestParam(required = false) Long zoneId,
                            Model model) {
        model.addAttribute("summary", dashboardPortfolioService.buildSnapshot(sellerId, zoneId));
        model.addAttribute("sellerOptions", dashboardPortfolioService.getSellerOptions());
        model.addAttribute("zoneOptions", dashboardPortfolioService.getZoneOptions());
        model.addAttribute("isAdminView", dashboardPortfolioService.isCurrentUserAdminView());
        return "pages/dashboard/index";
    }
}
