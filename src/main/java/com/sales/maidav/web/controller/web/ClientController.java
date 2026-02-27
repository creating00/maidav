package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.sale.AccountStatus;
import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.model.sale.CreditInstallment;
import com.sales.maidav.model.sale.InstallmentStatus;
import com.sales.maidav.repository.sale.CreditAccountRepository;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.service.client.ClientService;
import com.sales.maidav.service.client.DuplicateNationalIdException;
import com.sales.maidav.service.client.InvalidNationalIdException;
import com.sales.maidav.service.client.ZoneService;
import com.sales.maidav.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final UserService userService;
    private final CreditAccountRepository creditAccountRepository;
    private final CreditInstallmentRepository creditInstallmentRepository;
    private final ZoneService zoneService;

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public String list(
            @RequestParam(required = false) String search,
            Model model
    ) {

        if (search != null && !search.isBlank()) {
            model.addAttribute("clients", clientService.search(search));
            model.addAttribute("search", search);
        } else {
            model.addAttribute("clients", clientService.findAll());
        }

        return "pages/clients/index";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public String detail(@PathVariable Long id, Model model) {
        Client client = clientService.findById(id);
        List<CreditAccount> accounts = creditAccountRepository.findByClient_IdAndStatus(id, AccountStatus.OPEN);
        Map<Long, List<CreditInstallment>> installmentsByAccount = new HashMap<>();
        Map<Long, BigDecimal> currentInstallments = new HashMap<>();

        for (CreditAccount account : accounts) {
            List<CreditInstallment> installments =
                    creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(account.getId());
            installmentsByAccount.put(account.getId(), installments);
            BigDecimal currentAmount = null;
            for (CreditInstallment installment : installments) {
                if (installment.getStatus() != InstallmentStatus.PAID) {
                    BigDecimal paidAmount =
                            installment.getPaidAmount() == null ? BigDecimal.ZERO : installment.getPaidAmount();
                    currentAmount = installment.getAmount().subtract(paidAmount);
                    if (currentAmount.compareTo(BigDecimal.ZERO) < 0) {
                        currentAmount = BigDecimal.ZERO;
                    }
                    break;
                }
            }
            currentInstallments.put(account.getId(), currentAmount);
        }

        model.addAttribute("client", client);
        model.addAttribute("accounts", accounts);
        model.addAttribute("installmentsByAccount", installmentsByAccount);
        model.addAttribute("currentInstallments", currentInstallments);
        return "pages/clients/detail";
    }


    @GetMapping("/new")
    @PreAuthorize("hasAuthority('CLIENT_CREATE')")
    public String createForm(Model model) {
        Client client = new Client();
        model.addAttribute("client", client);
        populateFormModel(model, client);
        return "pages/clients/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLIENT_CREATE')")
    public String create(Client client, Model model, RedirectAttributes redirectAttributes) {
        try {
            clientService.create(client);
            redirectAttributes.addFlashAttribute("successMessage", "Cliente guardado correctamente");
            return "redirect:/clients";
        } catch (DuplicateNationalIdException | InvalidNationalIdException ex) {
            model.addAttribute("client", client);
            model.addAttribute("nationalIdError", ex.getMessage());
            populateFormModel(model, client);
            return "pages/clients/form";
        }
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('CLIENT_UPDATE')")
    public String editForm(@PathVariable Long id, Model model) {
        Client client = clientService.findById(id);
        model.addAttribute("client", client);
        populateFormModel(model, client);
        return "pages/clients/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENT_UPDATE')")
    public String update(@PathVariable Long id, Client client, Model model, RedirectAttributes redirectAttributes) {
        try {
            clientService.update(id, client);
            redirectAttributes.addFlashAttribute("successMessage", "Cliente guardado correctamente");
            return "redirect:/clients";
        } catch (DuplicateNationalIdException | InvalidNationalIdException ex) {
            client.setId(id);
            model.addAttribute("client", client);
            model.addAttribute("nationalIdError", ex.getMessage());
            populateFormModel(model, client);
            return "pages/clients/form";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('CLIENT_DELETE')")
    public String delete(@PathVariable Long id) {
        clientService.delete(id);
        return "redirect:/clients";
    }

    private void populateFormModel(Model model, Client client) {
        List<Client> recommendedByOptions = clientService.findAll()
                .stream()
                .filter(existing -> client.getId() == null || !existing.getId().equals(client.getId()))
                .collect(Collectors.toList());
        model.addAttribute("recommendedByOptions", recommendedByOptions);
        model.addAttribute("vendors", userService.findByRoleName("VENDEDOR"));
        model.addAttribute("zones", zoneService.findAll());
    }
}
