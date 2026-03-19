package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.service.sale.CollectionWorkbenchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Map;

@Controller
@RequestMapping("/collections")
public class MassPaymentController {

    private final CollectionWorkbenchService collectionWorkbenchService;

    public MassPaymentController(CollectionWorkbenchService collectionWorkbenchService) {
        this.collectionWorkbenchService = collectionWorkbenchService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public String index() {
        return "pages/collections/index";
    }

    @GetMapping("/lookup")
    @ResponseBody
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public ResponseEntity<?> lookup(@RequestParam Long id) {
        try {
            return ResponseEntity.ok(collectionWorkbenchService.lookup(id));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/register")
    @ResponseBody
    @PreAuthorize("hasAuthority('ARREARS_READ')")
    public ResponseEntity<?> register(@RequestParam Long accountId,
                                      @RequestParam BigDecimal amount,
                                      @RequestParam PaymentCollectionMethod paymentMethod,
                                      @RequestParam(required = false) String operationToken,
                                      Authentication authentication) {
        try {
            String registeredBy = authentication != null ? authentication.getName() : null;
            return ResponseEntity.ok(collectionWorkbenchService.registerPayment(accountId, amount, paymentMethod, registeredBy, operationToken));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", ex.getMessage()));
        }
    }
}

