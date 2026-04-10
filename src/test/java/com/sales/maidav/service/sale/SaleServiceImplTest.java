package com.sales.maidav.service.sale;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.model.sale.SaleItem;
import com.sales.maidav.model.sale.SaleStatus;
import com.sales.maidav.repository.product.ProductRepository;
import com.sales.maidav.repository.sale.CreditAccountRepository;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.repository.sale.SaleRepository;
import com.sales.maidav.repository.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleServiceImplTest {

    @Mock
    private SaleRepository saleRepository;
    @Mock
    private SaleItemRepository saleItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CreditAccountRepository creditAccountRepository;
    @Mock
    private CreditInstallmentRepository creditInstallmentRepository;
    @Mock
    private UserRepository userRepository;

    private SaleServiceImpl saleService;

    @BeforeEach
    void setUp() {
        saleService = new SaleServiceImpl(
                saleRepository,
                saleItemRepository,
                productRepository,
                creditAccountRepository,
                creditInstallmentRepository,
                userRepository
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findAllUsesNewestFirstRepositoryMethod() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin@maidav.com",
                        "secret",
                        List.of(() -> "ROLE_ADMIN")
                )
        );

        Sale newest = new Sale();
        newest.setId(2L);
        Sale oldest = new Sale();
        oldest.setId(1L);
        when(saleRepository.findAllByOrderBySaleDateDescIdDesc()).thenReturn(List.of(newest, oldest));

        List<Sale> sales = saleService.findAll();

        assertThat(sales).containsExactly(newest, oldest);
        verify(saleRepository).findAllByOrderBySaleDateDescIdDesc();
    }

    @Test
    void voidSaleRestoresProductStockForRelatedItems() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin@maidav.com",
                        "secret",
                        List.of(() -> "ROLE_ADMIN")
                )
        );

        Sale sale = new Sale();
        sale.setId(7L);
        sale.setStatus(SaleStatus.ACTIVE);

        Product firstProduct = new Product();
        firstProduct.setStockAvailable(3);
        SaleItem firstItem = new SaleItem();
        firstItem.setProduct(firstProduct);
        firstItem.setQuantity(2);

        Product secondProduct = new Product();
        secondProduct.setStockAvailable(0);
        SaleItem secondItem = new SaleItem();
        secondItem.setProduct(secondProduct);
        secondItem.setQuantity(5);

        when(saleRepository.findById(7L)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findBySale_IdOrderByIdAsc(7L)).thenReturn(List.of(firstItem, secondItem));

        saleService.voidSale(7L);

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.VOID);
        assertThat(firstProduct.getStockAvailable()).isEqualTo(5);
        assertThat(secondProduct.getStockAvailable()).isEqualTo(5);
        verify(productRepository).save(firstProduct);
        verify(productRepository).save(secondProduct);
    }

    @Test
    void voidSaleDoesNothingWhenSaleIsAlreadyVoid() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin@maidav.com",
                        "secret",
                        List.of(() -> "ROLE_ADMIN")
                )
        );

        Sale sale = new Sale();
        sale.setId(8L);
        sale.setStatus(SaleStatus.VOID);

        when(saleRepository.findById(8L)).thenReturn(Optional.of(sale));

        saleService.voidSale(8L);

        verify(saleItemRepository, never()).findBySale_IdOrderByIdAsc(any());
        verify(productRepository, never()).save(any());
    }
}
