package com.sales.maidav.service.quote;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.quote.Quote;
import com.sales.maidav.model.quote.QuotePriceMode;
import com.sales.maidav.model.settings.CompanySettings;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.product.ProductRepository;
import com.sales.maidav.repository.quote.QuoteRepository;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.service.settings.CompanySettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteServiceImplTest {

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanySettingsService companySettingsService;

    private QuoteServiceImpl quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteServiceImpl(
                quoteRepository,
                productRepository,
                userRepository,
                companySettingsService,
                new QuoteCalculator()
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBuildsQuoteSnapshotForCurrentSeller() {
        authenticate("seller@maidav.com");

        User seller = new User();
        seller.setId(9L);
        seller.setEmail("seller@maidav.com");

        Product product = product();

        when(userRepository.findByEmail("seller@maidav.com")).thenReturn(Optional.of(seller));
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        when(companySettingsService.getSettings()).thenReturn(new CompanySettings());
        when(quoteRepository.save(any(Quote.class))).thenAnswer(invocation -> {
            Quote saved = invocation.getArgument(0);
            saved.setId(14L);
            return saved;
        });

        Quote quote = quoteService.create(QuotePriceMode.RETAIL, List.of(new QuoteItemInput(7L, 2)));

        assertThat(quote.getSeller()).isEqualTo(seller);
        assertThat(quote.getQuoteNumber()).isEqualTo("P-000014");
        assertThat(quote.getItemCount()).isEqualTo(1);
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("40000.00");
        assertThat(quote.getPricingBaseAmount()).isEqualByComparingTo("40000.00");
        assertThat(quote.getCashAmount()).isEqualByComparingTo("52000.00");
        assertThat(quote.getDebitAmount()).isEqualByComparingTo("60000.00");
        assertThat(quote.getItems()).hasSize(1);
        assertThat(quote.getItems().get(0).getProductCode()).isEqualTo("PRD-DEMO");
        assertThat(quote.getPlanOptions()).hasSize(4);
        assertThat(quote.getPlanOptions().get(0).getTitle()).isEqualTo("Son 144 dias de $700");
        assertThat(quote.getPlanOptions().get(1).getPromoText()).isEqualTo("Si abonas en efectivo pagas la cuota $6200");
    }

    @Test
    void findAllUsesCurrentSellerVisibility() {
        authenticate("seller@maidav.com");

        User seller = new User();
        seller.setId(15L);
        Quote first = new Quote();
        Quote second = new Quote();

        when(userRepository.findByEmail("seller@maidav.com")).thenReturn(Optional.of(seller));
        when(quoteRepository.findBySeller_IdOrderByCreatedAtDescIdDesc(15L)).thenReturn(List.of(first, second));

        List<Quote> quotes = quoteService.findAll();

        assertThat(quotes).containsExactly(first, second);
        verify(quoteRepository).findBySeller_IdOrderByCreatedAtDescIdDesc(15L);
    }

    @Test
    void findByIdRejectsQuotesOutsideCurrentSellerScope() {
        authenticate("seller@maidav.com");

        User seller = new User();
        seller.setId(15L);
        when(userRepository.findByEmail("seller@maidav.com")).thenReturn(Optional.of(seller));
        when(quoteRepository.findByIdAndSeller_Id(3L, 15L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quoteService.findById(3L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Presupuesto no encontrado");
    }

    @Test
    void findByIdInitializesCollectionsWithSeparateAccess() {
        authenticate("seller@maidav.com");

        User seller = new User();
        seller.setId(15L);

        Quote quote = mock(Quote.class);

        when(userRepository.findByEmail("seller@maidav.com")).thenReturn(Optional.of(seller));
        when(quoteRepository.findByIdAndSeller_Id(8L, 15L)).thenReturn(Optional.of(quote));
        when(quote.getItems()).thenReturn(List.of());
        when(quote.getPlanOptions()).thenReturn(List.of());

        Quote loaded = quoteService.findById(8L);

        assertThat(loaded).isSameAs(quote);
        verify(quote).getItems();
        verify(quote).getPlanOptions();
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "secret", List.of(() -> "QUOTE_READ"))
        );
    }

    private Product product() {
        Product product = new Product();
        product.setId(7L);
        product.setProductCode("PRD-DEMO");
        product.setDescription("Producto Demo");
        product.setPriceRetail(new BigDecimal("20000.00"));
        product.setPriceWholesale(new BigDecimal("18000.00"));
        product.setCost(new BigDecimal("10000.00"));
        product.setVatRate(new BigDecimal("21.00"));
        product.setImagePath("/uploads/products/demo.png");
        return product;
    }
}
