package com.sales.maidav.web.controller.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sales.maidav.service.quote.InvalidQuoteException;
import com.sales.maidav.service.quote.QuoteSalePreparation;
import com.sales.maidav.service.quote.QuoteSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/quotes/{id}/use")
@PreAuthorize("hasAuthority('QUOTE_READ') and hasAuthority('SALES_CREATE')")
@RequiredArgsConstructor
public class QuoteSaleController {
    private final QuoteSaleService quoteSaleService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String preview(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return transfer(id, null, model, redirectAttributes);
    }

    @PostMapping
    public String accept(@PathVariable Long id, @RequestParam String fingerprint,
                         Model model, RedirectAttributes redirectAttributes) {
        return transfer(id, fingerprint, model, redirectAttributes);
    }

    private String transfer(Long id, String fingerprint, Model model, RedirectAttributes redirectAttributes) {
        try {
            // Re-read catalog prices on acceptance, so a concurrent adjustment needs a new confirmation.
            QuoteSalePreparation preparation = quoteSaleService.prepare(id);
            if (!preparation.changes().isEmpty() && !preparation.fingerprint().equals(fingerprint)) {
                model.addAttribute("preparation", preparation);
                return "pages/quotes/use";
            }
            redirectAttributes.addFlashAttribute("quoteDraftState", objectMapper.writeValueAsString(
                    Map.of("priceMode", preparation.priceMode(), "rows", preparation.rows())));
            redirectAttributes.addFlashAttribute("sourceQuoteNumber", preparation.quoteNumber());
            return "redirect:/sales/new";
        } catch (InvalidQuoteException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/quotes";
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo preparar el presupuesto para la venta", ex);
        }
    }
}
