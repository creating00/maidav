package com.sales.maidav.web.controller.web;

import com.sales.maidav.service.client.ClientService;
import com.sales.maidav.service.product.ProductService;
import com.sales.maidav.service.sale.CreditAccountService;
import com.sales.maidav.service.sale.SaleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final ClientService clientService;
    private final SaleService saleService;
    private final CreditAccountService creditAccountService;
    private final ProductService productService;

    public DashboardController(ClientService clientService,
                               SaleService saleService,
                               CreditAccountService creditAccountService,
                               ProductService productService) {
        this.clientService = clientService;
        this.saleService = saleService;
        this.creditAccountService = creditAccountService;
        this.productService = productService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        model.addAttribute("clientsCount", clientService.count());
        model.addAttribute("salesCount", saleService.countActive());
        model.addAttribute("overdueClientsCount", creditAccountService.countMoroseClients());
        model.addAttribute("todaySalesCount", saleService.countToday());
        model.addAttribute("productsCount", productService.count());

        return "pages/dashboard/index";
    }
}
