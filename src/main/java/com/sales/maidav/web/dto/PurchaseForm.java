package com.sales.maidav.web.dto;

import com.sales.maidav.model.sale.PaymentCollectionMethod;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PurchaseForm {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate purchaseDate;
    private Long providerId;
    private PaymentCollectionMethod paymentMethod;
    private String notes;
    private String operationKey;
    private List<Item> items = new ArrayList<>();

    @Getter
    @Setter
    public static class Item {
        private Long productId;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
