package com.sales.maidav.service.purchase;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.purchase.*;
import com.sales.maidav.repository.product.ProductRepository;
import com.sales.maidav.repository.product.ProviderRepository;
import com.sales.maidav.repository.purchase.*;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.web.dto.PurchaseForm;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseServiceImpl implements PurchaseService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private final PurchaseRepository purchaseRepository;
    private final PurchaseIdempotencyKeyRepository keyRepository;
    private final ProductRepository productRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Purchase create(PurchaseForm form, Long userId) {
        String key = validateKey(form.getOperationKey(), userId);
        keyRepository.insertIfAbsent(userId, key);
        PurchaseIdempotencyKey request = keyRepository.findForUpdate(userId, key)
                .orElseThrow(() -> new IllegalStateException("No se pudo registrar el intento de compra"));
        if (request.getPurchase() != null) {
            return request.getPurchase();
        }
        validateHeader(form);

        Purchase purchase = new Purchase();
        purchase.setRegisteredBy(userRepository.findById(userId)
                .orElseThrow(() -> new InvalidPurchaseException("Usuario no encontrado")));
        purchase.setPurchaseDate(form.getPurchaseDate());
        purchase.setPaymentMethod(form.getPaymentMethod());
        purchase.setNotes(form.getNotes() == null ? null : form.getNotes().trim());
        if (form.getProviderId() != null) {
            var provider = providerRepository.findById(form.getProviderId())
                    .orElseThrow(() -> new InvalidPurchaseException("Proveedor no encontrado"));
            purchase.setProvider(provider);
            purchase.setProviderName(provider.getName());
        }

        BigDecimal total = BigDecimal.ZERO;
        Map<Long, Integer> stockChanges = new TreeMap<>();
        for (PurchaseForm.Item input : form.getItems()) {
            if (input == null || input.getProductId() == null || input.getQuantity() == null || input.getQuantity() <= 0) {
                throw new InvalidPurchaseException("Seleccione producto y cantidad entera positiva en cada ítem");
            }
            validateAmount(input.getUnitPrice(), "El precio unitario");
            Product product = productRepository.findById(input.getProductId())
                    .orElseThrow(() -> new InvalidPurchaseException("Producto no encontrado"));
            BigDecimal lineTotal = input.getUnitPrice().multiply(BigDecimal.valueOf(input.getQuantity()));
            validateAmount(lineTotal, "El subtotal");
            total = total.add(lineTotal);
            validateAmount(total, "El total");
            try {
                stockChanges.merge(product.getId(), input.getQuantity(), Math::addExact);
            } catch (ArithmeticException ex) {
                throw new InvalidPurchaseException("La cantidad acumulada del producto es demasiado grande");
            }
            PurchaseItem item = new PurchaseItem();
            item.setPurchase(purchase);
            item.setProduct(product);
            item.setProductCode(product.getProductCode());
            item.setProductDescription(product.getDescription());
            item.setQuantity(input.getQuantity());
            item.setUnitPrice(input.getUnitPrice().setScale(2));
            item.setLineTotal(lineTotal.setScale(2));
            purchase.getItems().add(item);
        }
        // Match the sale stock lock order to avoid deadlocks across multi-product operations.
        stockChanges.forEach((productId, quantity) -> {
            if (productRepository.incrementStockForPurchase(productId, quantity) != 1) {
                throw new InvalidPurchaseException("No se pudo incrementar el stock: producto inexistente o límite excedido");
            }
        });
        purchase.setTotalAmount(total.setScale(2));
        purchaseRepository.save(purchase);
        request.setPurchase(purchase);
        return purchase;
    }

    @Override
    public Purchase findById(Long id) {
        return purchaseRepository.findDetailById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
    }

    @Override
    public Page<Purchase> findPage(LocalDate from, LocalDate to, Long providerId, Long productId, int page) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidPurchaseException("La fecha desde no puede ser posterior a la fecha hasta");
        }
        return purchaseRepository.findAll((root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (from != null) filters.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), from));
            if (to != null) filters.add(cb.lessThanOrEqualTo(root.get("purchaseDate"), to));
            if (providerId != null) filters.add(cb.equal(root.get("provider").get("id"), providerId));
            if (productId != null) {
                var subquery = query.subquery(Long.class);
                var item = subquery.from(PurchaseItem.class);
                subquery.select(item.get("id")).where(cb.equal(item.get("purchase"), root),
                        cb.equal(item.get("product").get("id"), productId));
                filters.add(cb.exists(subquery));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        }, PageRequest.of(Math.max(0, page), 20, Sort.by(Sort.Direction.DESC, "purchaseDate", "id")));
    }

    private void validateHeader(PurchaseForm form) {
        if (form.getPurchaseDate() == null || form.getPurchaseDate().isAfter(LocalDate.now())) {
            throw new InvalidPurchaseException("Ingrese una fecha de compra válida, hasta hoy");
        }
        if (form.getPaymentMethod() == null) {
            throw new InvalidPurchaseException("Seleccione la forma de pago");
        }
        if (form.getNotes() != null && form.getNotes().length() > 2000) {
            throw new InvalidPurchaseException("Las observaciones admiten hasta 2000 caracteres");
        }
        if (form.getItems() == null || form.getItems().isEmpty() || form.getItems().size() > 100) {
            throw new InvalidPurchaseException("Agregue entre 1 y 100 ítems");
        }
    }

    private void validateAmount(BigDecimal amount, String label) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidPurchaseException(label + " debe ser positivo, con hasta 2 decimales y hasta $" + MAX_AMOUNT);
        }
    }

    private String validateKey(String key, Long userId) {
        try {
            if (userId == null || key == null || key.length() != 36) throw new IllegalArgumentException();
            return UUID.fromString(key).toString();
        } catch (IllegalArgumentException ex) {
            throw new InvalidPurchaseException("No se pudo validar el intento de compra. Abra un formulario nuevo");
        }
    }
}
