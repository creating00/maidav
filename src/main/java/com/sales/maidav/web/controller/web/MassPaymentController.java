package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.service.sale.MassPaymentImportService;
import com.sales.maidav.service.sale.MassPaymentImportSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/collections")
public class MassPaymentController {

    private final MassPaymentImportService massPaymentImportService;

    public MassPaymentController(MassPaymentImportService massPaymentImportService) {
        this.massPaymentImportService = massPaymentImportService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String index(Model model) {
        model.addAttribute("paymentMethods", PaymentCollectionMethod.values());
        return "pages/collections/index";
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String importFile(@RequestParam("file") MultipartFile file,
                             @RequestParam(required = false, defaultValue = "BANK") PaymentCollectionMethod defaultPaymentMethod,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            String registeredBy = authentication != null ? authentication.getName() : null;
            MassPaymentImportSummary summary = massPaymentImportService.importWorkbook(file, defaultPaymentMethod, registeredBy);
            redirectAttributes.addFlashAttribute("importSummary", summary);
            if (summary.getSuccessCount() > 0) {
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        "Importacion completada: " + summary.getSuccessCount() + " pago(s) impactado(s)"
                );
            }
            if (summary.hasErrors()) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "Se detectaron " + summary.getErrorCount() + " fila(s) con error durante la importacion"
                );
            }
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/collections";
    }
}
