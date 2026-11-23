package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.dashboard.DashboardPortfolioService;
import com.sales.maidav.service.campaign.CampaignService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

    private final DashboardPortfolioService dashboardPortfolioService;
    private final CampaignService campaignService;

    public DashboardController(DashboardPortfolioService dashboardPortfolioService, CampaignService campaignService) {
        this.dashboardPortfolioService = dashboardPortfolioService;
        this.campaignService = campaignService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) Long sellerId,
                            @RequestParam(required = false) Long zoneId,
                            Authentication authentication, Model model) {
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CAMPAIGN_READ"))) {
            model.addAttribute("activeOffers", campaignService.findActiveHighlights());
        }
        model.addAttribute("summary", dashboardPortfolioService.buildSnapshot(sellerId, zoneId));
        model.addAttribute("sellerOptions", dashboardPortfolioService.getSellerOptions());
        model.addAttribute("zoneOptions", dashboardPortfolioService.getZoneOptions());
        model.addAttribute("isAdminView", dashboardPortfolioService.isCurrentUserAdminView());
        return "pages/dashboard/index";
    }
}
