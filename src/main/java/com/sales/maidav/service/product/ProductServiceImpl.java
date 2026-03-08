package com.sales.maidav.service.product;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.model.product.ProductPriceAdjustment;
import com.sales.maidav.model.product.ProductPriceAdjustmentItem;
import com.sales.maidav.model.product.PriceAdjustmentScope;
import com.sales.maidav.model.product.PriceAdjustmentType;
import com.sales.maidav.repository.product.ProductPriceAdjustmentItemRepository;
import com.sales.maidav.repository.product.ProductPriceAdjustmentRepository;
import com.sales.maidav.repository.product.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductPriceAdjustmentRepository productPriceAdjustmentRepository;
    private final ProductPriceAdjustmentItemRepository productPriceAdjustmentItemRepository;

    @Override
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Override
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

    @Override
    public Product create(Product product) {
        validate(product);
        ensureUniqueCode(product.getProvider().getId(), product.getProductCode(), null);
        recalcPrices(product);
        return productRepository.save(product);
    }

    @Override
    public Product update(Long id, Product data) {
        validate(data);
        ensureUniqueCode(data.getProvider().getId(), data.getProductCode(), id);

        Product product = findById(id);
        product.setProvider(data.getProvider());
        product.setProductCode(data.getProductCode());
        product.setBarcode(data.getBarcode());
        product.setDescription(data.getDescription());
        product.setCost(data.getCost());
        product.setPriceWholesaleNet(data.getPriceWholesaleNet());
        product.setPriceRetailNet(data.getPriceRetailNet());
        product.setVatRate(data.getVatRate());
        product.setStockAvailable(data.getStockAvailable());
        product.setStockMin(data.getStockMin());
        product.setStockMax(data.getStockMax());
        product.setImagePath(data.getImagePath());

        recalcPrices(product);
        return product;
    }

    @Override
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    @Override
    public long count() {
        return productRepository.count();
    }

    @Override
    public long countLowStock() {
        return productRepository.countLowStock();
    }

    @Override
    public List<Product> findLowStock() {
        return productRepository.findLowStock();
    }

    @Override
    public long bulkAdjustPrices(BigDecimal percentage, Long providerId, PriceAdjustmentType adjustmentType, PriceAdjustmentScope scope) {
        if (percentage == null || percentage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidProductException("El porcentaje debe ser mayor a cero");
        }
        if (adjustmentType == null) {
            throw new InvalidProductException("Tipo de ajuste invalido");
        }
        if (scope == null) {
            throw new InvalidProductException("Alcance de ajuste invalido");
        }

        List<Product> products = providerId == null
                ? productRepository.findAll()
                : productRepository.findByProvider_Id(providerId);

        if (products.isEmpty()) {
            throw new InvalidProductException("No hay productos para actualizar");
        }

        BigDecimal factor = calculateFactor(percentage, adjustmentType);

        for (Product product : products) {
            applyFactor(product, factor, scope);
        }

        ProductPriceAdjustment adjustment = new ProductPriceAdjustment();
        adjustment.setAdjustmentType(adjustmentType);
        adjustment.setPercentage(percentage.setScale(4, RoundingMode.HALF_UP));
        adjustment.setFactorApplied(factor.setScale(8, RoundingMode.HALF_UP));
        adjustment.setScope(scope);
        adjustment.setProvider(providerId == null ? null : products.get(0).getProvider());
        adjustment.setProductsAffected(products.size());
        adjustment.setCreatedBy(currentUsername());
        adjustment.setUndone(false);
        productPriceAdjustmentRepository.save(adjustment);

        List<ProductPriceAdjustmentItem> items = products.stream()
                .map(product -> {
                    ProductPriceAdjustmentItem item = new ProductPriceAdjustmentItem();
                    item.setAdjustment(adjustment);
                    item.setProduct(product);
                    return item;
                })
                .toList();
        productPriceAdjustmentItemRepository.saveAll(items);

        return products.size();
    }

    @Override
    public List<ProductPriceAdjustment> findRecentAdjustments() {
        return productPriceAdjustmentRepository.findTop20ByOrderByCreatedAtDesc();
    }

    @Override
    public Map<Long, String> findAdjustmentProductCodes(List<Long> adjustmentIds) {
        if (adjustmentIds == null || adjustmentIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> codesByAdjustment = productPriceAdjustmentItemRepository
                .findByAdjustment_IdIn(adjustmentIds)
                .stream()
                .collect(Collectors.groupingBy(
                        item -> item.getAdjustment().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(item -> item.getProduct().getProductCode(), Collectors.toList())
                ));

        Map<Long, String> result = new LinkedHashMap<>();
        for (Long adjustmentId : adjustmentIds) {
            List<String> codes = codesByAdjustment.getOrDefault(adjustmentId, List.of()).stream()
                    .distinct()
                    .toList();
            if (codes.isEmpty()) {
                result.put(adjustmentId, "-");
                continue;
            }
            int maxVisible = 3;
            String visibleCodes = String.join(", ", codes.stream().limit(maxVisible).toList());
            if (codes.size() > maxVisible) {
                visibleCodes = visibleCodes + " +" + (codes.size() - maxVisible);
            }
            result.put(adjustmentId, visibleCodes);
        }
        return result;
    }

    @Override
    public void undoAdjustment(Long adjustmentId) {
        ProductPriceAdjustment adjustment = productPriceAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new InvalidProductException("Ajuste no encontrado"));

        if (adjustment.isUndone()) {
            throw new InvalidProductException("El ajuste ya fue deshecho");
        }

        List<Long> productIds = productPriceAdjustmentItemRepository.findByAdjustment_Id(adjustmentId).stream()
                .map(item -> item.getProduct().getId())
                .distinct()
                .toList();

        if (productIds.isEmpty()) {
            throw new InvalidProductException("No hay productos para deshacer el ajuste");
        }

        BigDecimal inverseFactor = BigDecimal.ONE.divide(adjustment.getFactorApplied(), 12, RoundingMode.HALF_UP);
        List<Product> products = productRepository.findAllById(productIds);

        PriceAdjustmentScope scope = adjustment.getScope() == null ? PriceAdjustmentScope.ALL : adjustment.getScope();
        for (Product product : products) {
            applyFactor(product, inverseFactor, scope);
        }

        adjustment.setUndone(true);
        adjustment.setUndoneAt(LocalDateTime.now());
        adjustment.setUndoneBy(currentUsername());
    }

    private void ensureUniqueCode(Long providerId, String productCode, Long id) {
        if (providerId == null || productCode == null || productCode.isBlank()) {
            return;
        }
        boolean exists = (id == null)
                ? productRepository.existsByProvider_IdAndProductCode(providerId, productCode)
                : productRepository.existsByProvider_IdAndProductCodeAndIdNot(providerId, productCode, id);
        if (exists) {
            throw new DuplicateProductCodeException("Codigo ya existente para ese proveedor");
        }
    }

    private void validate(Product product) {
        if (product.getProvider() == null || product.getProvider().getId() == null) {
            throw new InvalidProductException("Debe seleccionar un proveedor");
        }
        if (product.getProductCode() == null || product.getProductCode().isBlank()) {
            throw new InvalidProductException("El codigo de producto es obligatorio");
        }
        if (product.getDescription() == null || product.getDescription().isBlank()) {
            throw new InvalidProductException("La descripcion es obligatoria");
        }
        if (product.getCost() == null) {
            throw new InvalidProductException("El costo es obligatorio");
        }
        if (product.getPriceWholesaleNet() == null || product.getPriceRetailNet() == null) {
            throw new InvalidProductException("Los precios sin IVA son obligatorios");
        }
        if (!isValidVatRate(product.getVatRate())) {
            throw new InvalidProductException("IVA invalido");
        }
        if (product.getStockAvailable() == null || product.getStockMin() == null || product.getStockMax() == null) {
            throw new InvalidProductException("Stock disponible, minimo y maximo son obligatorios");
        }
        if (product.getStockMin() < 0 || product.getStockMax() < 0 || product.getStockAvailable() < 0) {
            throw new InvalidProductException("Stock no puede ser negativo");
        }
        if (product.getStockMin() > product.getStockMax()) {
            throw new InvalidProductException("Stock minimo no puede ser mayor que stock maximo");
        }
        if (product.getStockAvailable() > product.getStockMax()) {
            throw new InvalidProductException("Stock disponible no puede superar el maximo");
        }
    }

    private void recalcPrices(Product product) {
        BigDecimal vatMultiplier = BigDecimal.ONE
                .add(product.getVatRate().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        product.setPriceWholesale(
                product.getPriceWholesaleNet().multiply(vatMultiplier).setScale(2, RoundingMode.HALF_UP)
        );
        product.setPriceRetail(
                product.getPriceRetailNet().multiply(vatMultiplier).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal calculateFactor(BigDecimal percentage, PriceAdjustmentType adjustmentType) {
        BigDecimal decimalPercentage = percentage.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        if (adjustmentType == PriceAdjustmentType.INCREASE) {
            return BigDecimal.ONE.add(decimalPercentage);
        }
        if (decimalPercentage.compareTo(BigDecimal.ONE) >= 0) {
            throw new InvalidProductException("El descuento debe ser menor a 100%");
        }
        return BigDecimal.ONE.subtract(decimalPercentage);
    }

    private void applyFactor(Product product, BigDecimal factor, PriceAdjustmentScope scope) {
        if (scope == PriceAdjustmentScope.ALL) {
            product.setCost(product.getCost().multiply(factor).setScale(2, RoundingMode.HALF_UP));
            product.setPriceWholesaleNet(product.getPriceWholesaleNet().multiply(factor).setScale(2, RoundingMode.HALF_UP));
            product.setPriceRetailNet(product.getPriceRetailNet().multiply(factor).setScale(2, RoundingMode.HALF_UP));
        } else if (scope == PriceAdjustmentScope.WHOLESALE) {
            product.setPriceWholesaleNet(product.getPriceWholesaleNet().multiply(factor).setScale(2, RoundingMode.HALF_UP));
        } else if (scope == PriceAdjustmentScope.RETAIL) {
            product.setPriceRetailNet(product.getPriceRetailNet().multiply(factor).setScale(2, RoundingMode.HALF_UP));
        }
        recalcPrices(product);
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "sistema";
        }
        return authentication.getName();
    }

    private boolean isValidVatRate(BigDecimal vatRate) {
        if (vatRate == null) {
            return false;
        }
        return vatRate.compareTo(BigDecimal.ZERO) == 0
                || vatRate.compareTo(new BigDecimal("10.50")) == 0
                || vatRate.compareTo(new BigDecimal("21.00")) == 0;
    }
}
