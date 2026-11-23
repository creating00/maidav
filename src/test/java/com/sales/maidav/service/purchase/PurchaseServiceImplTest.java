package com.sales.maidav.service.purchase;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.purchase.*;
import com.sales.maidav.model.sale.PaymentCollectionMethod;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.product.*;
import com.sales.maidav.repository.purchase.*;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.web.dto.PurchaseForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceImplTest {
    @Mock PurchaseRepository purchases;
    @Mock PurchaseIdempotencyKeyRepository keys;
    @Mock ProductRepository products;
    @Mock ProviderRepository providers;
    @Mock UserRepository users;
    @InjectMocks PurchaseServiceImpl service;
    PurchaseForm form;
    PurchaseIdempotencyKey key;

    @BeforeEach
    void setup() {
        form = new PurchaseForm();
        form.setPurchaseDate(LocalDate.now());
        form.setPaymentMethod(PaymentCollectionMethod.TRANSFER);
        form.setOperationKey(UUID.randomUUID().toString());
        form.setItems(List.of(item(2L, 3, "10.15")));
        key = new PurchaseIdempotencyKey();
    }

    @Test
    void aggregatesStockAndPreservesPriceAndDescriptionSnapshots() {
        prepare();
        form.setItems(List.of(item(2L, 3, "10.15"), item(2L, 2, "11.00")));
        when(products.incrementStockForPurchase(2L, 5)).thenReturn(1);
        Purchase result = service.create(form, 1L);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("52.45");
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().getFirst().getProductDescription()).isEqualTo("Descripción original");
        assertThat(key.getPurchase()).isSameAs(result);
        verify(products).incrementStockForPurchase(2L, 5);
        verify(products, never()).save(any());
    }

    @Test
    void repeatedRequestDoesNotMutateStock() {
        prepareKey();
        Purchase original = new Purchase();
        key.setPurchase(original);
        assertThat(service.create(form, 1L)).isSameAs(original);
        verifyNoInteractions(products, providers, users, purchases);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "10000000000"})
    void rejectsInvalidPrices(String price) {
        prepareKey();
        when(users.findById(1L)).thenReturn(Optional.of(new User()));
        form.getItems().getFirst().setUnitPrice(new BigDecimal(price));
        assertThatThrownBy(() -> service.create(form, 1L)).isInstanceOf(InvalidPurchaseException.class);
        verifyNoInteractions(products, purchases);
    }

    @Test
    void rejectsTotalOverflowBeforeMutatingStock() {
        prepare();
        form.setItems(List.of(item(2L, 1, "9999999999.99"), item(2L, 1, "0.01")));
        assertThatThrownBy(() -> service.create(form, 1L)).hasMessageContaining("total");
        verify(products, never()).incrementStockForPurchase(anyLong(), anyInt());
    }

    @Test
    void rejectsAccumulatedQuantityOverflow() {
        prepare();
        form.setItems(List.of(item(2L, Integer.MAX_VALUE, "0.01"), item(2L, 1, "0.01")));
        assertThatThrownBy(() -> service.create(form, 1L)).hasMessageContaining("cantidad acumulada");
        verify(products, never()).incrementStockForPurchase(anyLong(), anyInt());
    }

    @Test
    void doesNotSavePurchaseIfStockCannotBeIncremented() {
        prepare();
        assertThatThrownBy(() -> service.create(form, 1L)).hasMessageContaining("stock");
        verifyNoInteractions(purchases);
        assertThat(key.getPurchase()).isNull();
    }

    @Test
    void rejectsFutureDateAndInvalidFilterRange() {
        prepareKey();
        form.setPurchaseDate(LocalDate.now().plusDays(1));
        assertThatThrownBy(() -> service.create(form, 1L)).hasMessageContaining("fecha");
        assertThatThrownBy(() -> service.findPage(LocalDate.now(), LocalDate.now().minusDays(1), null, null, 0))
                .hasMessageContaining("desde");
        verifyNoInteractions(products, purchases, users);
    }

    @Test
    void rejectsMalformedKeyBeforeAccessingDatabase() {
        form.setOperationKey("invalid");
        assertThatThrownBy(() -> service.create(form, 1L)).hasMessageContaining("intento");
        verifyNoInteractions(keys, products, purchases);
    }

    private void prepareKey() {
        when(keys.findForUpdate(1L, form.getOperationKey())).thenReturn(Optional.of(key));
    }

    private void prepare() {
        prepareKey();
        when(users.findById(1L)).thenReturn(Optional.of(new User()));
        Product product = new Product();
        product.setId(2L);
        product.setProductCode("P-2");
        product.setDescription("Descripción original");
        when(products.findById(2L)).thenReturn(Optional.of(product));
    }

    private PurchaseForm.Item item(Long id, int quantity, String price) {
        var item = new PurchaseForm.Item();
        item.setProductId(id);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(price));
        return item;
    }
}
