package com.sales.maidav.service.sale;

import com.sales.maidav.model.sale.Sale;
import com.sales.maidav.repository.product.ProductRepository;
import com.sales.maidav.repository.sale.CreditAccountRepository;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.repository.sale.SaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    private SaleServiceImpl saleService;

    @BeforeEach
    void setUp() {
        saleService = new SaleServiceImpl(
                saleRepository,
                saleItemRepository,
                productRepository,
                creditAccountRepository,
                creditInstallmentRepository
        );
    }

    @Test
    void findAllUsesNewestFirstRepositoryMethod() {
        Sale newest = new Sale();
        newest.setId(2L);
        Sale oldest = new Sale();
        oldest.setId(1L);
        when(saleRepository.findAllByOrderBySaleDateDescIdDesc()).thenReturn(List.of(newest, oldest));

        List<Sale> sales = saleService.findAll();

        assertThat(sales).containsExactly(newest, oldest);
        verify(saleRepository).findAllByOrderBySaleDateDescIdDesc();
    }
}
