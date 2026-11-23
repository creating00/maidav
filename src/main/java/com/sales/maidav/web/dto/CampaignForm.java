package com.sales.maidav.web.dto;

import com.sales.maidav.model.campaign.DiscountMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Getter
@Setter
public class CampaignForm {
    private String name;
    private String description;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    private String operationKey;
    private List<Item> items = new ArrayList<>();
    @Getter
    @Setter
    public static class Item {
        private Long productId;
        private DiscountMode discountMode = DiscountMode.FIXED_PRICE;
        private BigDecimal discountValue;
    }
}
