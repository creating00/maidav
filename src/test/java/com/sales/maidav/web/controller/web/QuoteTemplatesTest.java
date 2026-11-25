package com.sales.maidav.web.controller.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.quote.Quote;
import com.sales.maidav.model.quote.QuotePriceMode;
import com.sales.maidav.model.user.User;
import com.sales.maidav.service.quote.QuoteSalePreparation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Renders the production templates and exports fixtures for the Chromium checks. */
@SpringJUnitConfig(QuoteTemplatesTest.Config.class)
@WebAppConfiguration
class QuoteTemplatesTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminAndSellerCardsShowTheirAuthorizedActions() throws Exception {
        String admin = render("index", true);
        assertThat(admin).contains("Pasar a Venta", "Anclar como Plantilla", "Desanclar plantilla", "Anclado");
        String seller = render("index", false);
        assertThat(seller).contains("Usar presupuesto", "bg-emerald-50", "Anclado")
                .doesNotContain("Pasar a Venta", "Anclar como Plantilla", "Desanclar plantilla");
    }

    @Test
    void modalAndSalesDraftRenderWithActualTemplateExpressions() throws Exception {
        assertThat(render("use", false)).contains("Licuadora Dheli (PDR-6P45AB) cambió", "$20000.00", "$25000.00", "Aceptar");
        assertThat(render("sale", false)).contains("quoteDraftStateInput", "P-000008", "&quot;rows&quot;");
    }

    private String render(String page, boolean admin) throws Exception {
        String email = admin ? "admin@maidav.com" : "seller@maidav.com";
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a",
                List.of(new SimpleGrantedAuthority(admin ? "ROLE_ADMIN" : "ROLE_VENDEDOR"),
                        new SimpleGrantedAuthority("QUOTE_READ"), new SimpleGrantedAuthority("QUOTE_CREATE"),
                        new SimpleGrantedAuthority("QUOTE_DELETE"), new SimpleGrantedAuthority("SALES_CREATE"))));
        String html = mvc.perform(get("/preview/" + page)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        Path directory = Path.of("target", "quotes-preview");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(page + (admin ? "-admin" : "-seller") + ".html"), html);
        return html;
    }

    @Configuration
    @EnableWebMvc
    static class Config {
        @Bean Views views() { return new Views(); }
        @Bean DefaultWebSecurityExpressionHandler webSecurityExpressionHandler() {
            return new DefaultWebSecurityExpressionHandler();
        }
        @Bean ThymeleafViewResolver viewResolver(ApplicationContext context) {
            SpringResourceTemplateResolver templates = new SpringResourceTemplateResolver();
            templates.setApplicationContext(context);
            templates.setPrefix("classpath:/templates/");
            templates.setSuffix(".html");
            templates.setCharacterEncoding("UTF-8");
            SpringTemplateEngine engine = new SpringTemplateEngine();
            engine.setTemplateResolver(templates);
            engine.addDialect(new SpringSecurityDialect());
            ThymeleafViewResolver views = new ThymeleafViewResolver();
            views.setTemplateEngine(engine);
            views.setCharacterEncoding("UTF-8");
            return views;
        }
    }

    @Controller
    static class Views {
        @GetMapping("/preview/{page}")
        String view(@PathVariable String page, Model model) throws Exception {
            User admin = new User();
            admin.setId(1L);
            admin.setEmail("admin@maidav.com");
            Quote pinned = quote(8L, admin, true);
            model.addAttribute("lowStockCount", 0);
            model.addAttribute("currentPath", "/quotes");
            if (page.equals("index")) {
                User current = new User();
                current.setEmail(SecurityContextHolder.getContext().getAuthentication().getName());
                model.addAttribute("quotes", List.of(pinned, quote(9L, current, false)));
                return "pages/quotes/index";
            }
            var rows = List.of(new QuoteSalePreparation.Row(7L, 3, new BigDecimal("25000.00")),
                    new QuoteSalePreparation.Row(9L, 2, new BigDecimal("8000.00")));
            if (page.equals("use")) {
                model.addAttribute("preparation", new QuoteSalePreparation(8L, "P-000008", QuotePriceMode.RETAIL,
                        rows, List.of(new QuoteSalePreparation.PriceChange("Licuadora Dheli", "PDR-6P45AB",
                        new BigDecimal("20000.00"), new BigDecimal("25000.00"))), "current"));
                return "pages/quotes/use";
            }
            model.addAttribute("editing", false);
            model.addAttribute("isAdmin", false);
            model.addAttribute("clients", List.of());
            model.addAttribute("products", List.of(product(7L, "PDR-6P45AB", "Licuadora Dheli", "25000.00"),
                    product(9L, "PRD-9", "Yogurtera", "8000.00")));
            model.addAttribute("sellers", List.of());
            model.addAttribute("idempotencyKey", "preview");
            model.addAttribute("calculatorConfig", Map.ofEntries(Map.entry("recargo", 1.26),
                    Map.entry("multContado", 1.30), Map.entry("multDebito", 1.50), Map.entry("dias", 144),
                    Map.entry("intDia", 2), Map.entry("semanas", 13), Map.entry("intSem", 2),
                    Map.entry("mesesCorto", 4), Map.entry("intMesCorto", 2), Map.entry("mesesLargo", 8),
                    Map.entry("intMesLargo", 2.50), Map.entry("monthlyLongMinCost", 0)));
            model.addAttribute("sourceQuoteNumber", "P-000008");
            model.addAttribute("quoteDraftState", new ObjectMapper().writeValueAsString(
                    Map.of("priceMode", "RETAIL", "rows", rows)));
            return "pages/sales/form";
        }

        private Quote quote(Long id, User owner, boolean pinned) {
            Quote quote = new Quote();
            quote.setId(id);
            quote.setSeller(owner);
            quote.setPinned(pinned);
            quote.setQuoteNumber("P-00000" + id);
            ReflectionTestUtils.setField(quote, "createdAt", LocalDateTime.of(2026, 11, 24, 10, 0));
            quote.setPriceMode(QuotePriceMode.RETAIL);
            quote.setProductSummary("Licuadora Dheli");
            quote.setItemCount(1);
            quote.setTotalAmount(new BigDecimal("60000.00"));
            quote.setCashAmount(new BigDecimal("55000.00"));
            quote.setDebitAmount(new BigDecimal("60000.00"));
            return quote;
        }

        private Product product(Long id, String code, String description, String price) {
            Product product = new Product();
            product.setId(id);
            product.setProductCode(code);
            product.setDescription(description);
            product.setPriceRetail(new BigDecimal(price));
            product.setPriceWholesale(new BigDecimal(price));
            product.setCost(new BigDecimal("1000.00"));
            product.setVatRate(new BigDecimal("21"));
            product.setStockAvailable(10);
            return product;
        }
    }
}
