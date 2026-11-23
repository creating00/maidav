package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.campaign.CampaignFilter;
import com.sales.maidav.service.campaign.*;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.user.UserService;
import com.sales.maidav.web.dto.CampaignForm;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/campaigns")
@RequiredArgsConstructor
public class CampaignController {
    private final CampaignService campaignService;
    private final ProductService productService;
    private final UserService userService;

    @InitBinder("campaignForm")
    void configureBinding(WebDataBinder binder) {
        binder.setAllowedFields("name", "description", "startDate", "endDate", "operationKey", "items[*].productId", "items[*].discountMode", "items[*].discountValue");
        binder.setAutoGrowCollectionLimit(100);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    public String list(@RequestParam(defaultValue = "ACTIVE") CampaignFilter state, @RequestParam(defaultValue = "0") int page, Model model) {
        var campaigns = campaignService.findPage(state, page);
        if (campaigns.getTotalPages() > 0 && page >= campaigns.getTotalPages()) campaigns = campaignService.findPage(state, campaigns.getTotalPages() - 1);
        model.addAttribute("campaigns", campaigns);
        model.addAttribute("state", state);
        model.addAttribute("states", CampaignFilter.values());
        return "pages/campaigns/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('CAMPAIGN_CREATE')")
    public String createForm(Model model) {
        CampaignForm form = new CampaignForm();
        form.setStartDate(LocalDate.now());
        form.setEndDate(LocalDate.now().plusDays(6));
        form.setOperationKey(UUID.randomUUID().toString());
        form.getItems().add(new CampaignForm.Item());
        model.addAttribute("campaignForm", form);
        model.addAttribute("products", productService.findAll());
        return "pages/campaigns/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CAMPAIGN_CREATE')")
    public String create(@ModelAttribute CampaignForm campaignForm, BindingResult errors, Authentication authentication, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) model.addAttribute("formError", "Revise las fechas, productos y valores de oferta");
        else try {
            var campaign = campaignService.create(campaignForm, userService.findByEmail(authentication.getName()).getId());
            redirect.addFlashAttribute("successMessage", "Campaña registrada correctamente");
            boolean canRead = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CAMPAIGN_READ"));
            return canRead ? "redirect:/campaigns/" + campaign.getId() : "redirect:/campaigns/new";
        } catch (InvalidCampaignException ex) { model.addAttribute("formError", ex.getMessage()); }
        if (campaignForm.getItems() == null || campaignForm.getItems().isEmpty()) campaignForm.setItems(new ArrayList<>(List.of(new CampaignForm.Item())));
        model.addAttribute("products", productService.findAll());
        return "pages/campaigns/form";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("campaign", campaignService.findById(id));
        return "pages/campaigns/detail";
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CAMPAIGN_UPDATE')")
    public String cancel(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        campaignService.cancel(id, userService.findByEmail(authentication.getName()).getId());
        redirect.addFlashAttribute("successMessage", "Campaña cancelada. Las ventas ya confirmadas conservan sus condiciones");
        boolean canRead = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("CAMPAIGN_READ"));
        return canRead ? "redirect:/campaigns/" + id : "redirect:/dashboard";
    }
}
