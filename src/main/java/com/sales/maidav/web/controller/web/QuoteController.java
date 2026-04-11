package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.quote.Quote;
import com.sales.maidav.model.quote.QuotePriceMode;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.quote.QuoteDocumentService;
import com.sales.maidav.service.quote.InvalidQuoteException;
import com.sales.maidav.service.quote.QuoteCalculator;
import com.sales.maidav.service.quote.QuoteItemInput;
import com.sales.maidav.service.quote.QuoteService;
import com.sales.maidav.service.settings.CompanySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;
    private final ProductService productService;
    private final CompanySettingsService companySettingsService;
    private final QuoteCalculator quoteCalculator;
    private final QuoteDocumentService quoteDocumentService;

    @GetMapping
    @PreAuthorize("hasAuthority('QUOTE_READ')")
    public String list(@RequestParam(required = false) String q, Model model) {
        List<Quote> quotes = quoteService.findAll();
        if (q != null && !q.isBlank()) {
            String term = q.trim().toLowerCase(Locale.ROOT);
            quotes = quotes.stream()
                    .filter(quote -> contains(quote.getQuoteNumber(), term)
                            || contains(quote.getProductSummary(), term))
                    .toList();
        }
        model.addAttribute("q", q);
        model.addAttribute("quotes", quotes);
        return "pages/quotes/index";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('QUOTE_CREATE')")
    public String createForm(Model model) {
        addCreateFormAttributes(model);
        return "pages/quotes/form";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('QUOTE_READ')")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("quote", quoteService.findById(id));
        return "pages/quotes/detail";
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('QUOTE_READ')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        Quote quote = quoteService.findById(id);
        byte[] pdf = quoteDocumentService.generateQuotePdf(quote);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(pdfFileName(quote), java.nio.charset.StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/share/pdf")
    @PreAuthorize("hasAuthority('QUOTE_CREATE')")
    public ResponseEntity<byte[]> sharePdf(@RequestParam(required = false) QuotePriceMode priceMode,
                                           @RequestParam(name = "productIds", required = false) List<Long> productIds,
                                           @RequestParam(name = "quantities", required = false) List<Integer> quantities) {
        String previewNumber = quoteService.previewNextQuoteNumber();
        byte[] pdf = quoteDocumentService.generatePreviewPdf(previewNumber, priceMode, buildItems(productIds, quantities));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(previewNumber + ".pdf", java.nio.charset.StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('QUOTE_CREATE')")
    public String create(@RequestParam(required = false) QuotePriceMode priceMode,
                         @RequestParam(name = "productIds", required = false) List<Long> productIds,
                         @RequestParam(name = "quantities", required = false) List<Integer> quantities,
                         @RequestParam(required = false) String draftState,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        try {
            Quote quote = quoteService.create(priceMode, buildItems(productIds, quantities));
            redirectAttributes.addFlashAttribute("successMessage", "Presupuesto guardado correctamente");
            return "redirect:/quotes/" + quote.getId();
        } catch (InvalidQuoteException ex) {
            model.addAttribute("formError", ex.getMessage());
            model.addAttribute("draftState", draftState);
            addCreateFormAttributes(model);
            return "pages/quotes/form";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('QUOTE_DELETE')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        quoteService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Presupuesto eliminado correctamente");
        return "redirect:/quotes";
    }

    private void addCreateFormAttributes(Model model) {
        model.addAttribute("products", productService.findAll());
        model.addAttribute("calculatorConfig", quoteCalculator.readConfig(companySettingsService.getSettings()));
        if (!model.containsAttribute("previewQuoteNumber")) {
            model.addAttribute("previewQuoteNumber", quoteService.previewNextQuoteNumber());
        }
    }

    private List<QuoteItemInput> buildItems(List<Long> productIds, List<Integer> quantities) {
        List<QuoteItemInput> items = new ArrayList<>();
        if (productIds == null || quantities == null) {
            return items;
        }
        int size = Math.min(productIds.size(), quantities.size());
        for (int i = 0; i < size; i++) {
            Long productId = productIds.get(i);
            Integer quantity = quantities.get(i);
            if (productId == null || quantity == null) {
                continue;
            }
            items.add(new QuoteItemInput(productId, quantity));
        }
        return items;
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private String pdfFileName(Quote quote) {
        String quoteNumber = quote.getQuoteNumber() == null || quote.getQuoteNumber().isBlank()
                ? "presupuesto"
                : quote.getQuoteNumber().trim().replace(' ', '-');
        return quoteNumber + ".pdf";
    }
}
