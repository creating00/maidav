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
import java.util.concurrent.ThreadLocalRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private static final Ean13BarcodeSvgRenderer BARCODE_RENDERER = new Ean13BarcodeSvgRenderer();
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
        data.setId(id);
        validate(data);
        ensureUniqueCode(data.getProvider().getId(), data.getProductCode(), id);

        Product product = findById(id);
        PriceSnapshot before = PriceSnapshot.from(product);
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
        saveManualAdjustmentIfNeeded(product, before);
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

        Map<Long, PriceSnapshot> beforeSnapshots = new LinkedHashMap<>();
        for (Product product : products) {
            beforeSnapshots.put(product.getId(), PriceSnapshot.from(product));
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
                    PriceSnapshot before = beforeSnapshots.get(product.getId());
                    PriceSnapshot after = PriceSnapshot.from(product);
                    applySnapshots(item, before, after);
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

        List<ProductPriceAdjustmentItem> adjustmentItems = productPriceAdjustmentItemRepository.findByAdjustment_Id(adjustmentId);
        List<Product> products = productRepository.findAllById(productIds);

        boolean restoredFromSnapshots = false;
        Map<Long, ProductPriceAdjustmentItem> itemByProductId = adjustmentItems.stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), item -> item, (left, right) -> left, LinkedHashMap::new));

        for (Product product : products) {
            ProductPriceAdjustmentItem item = itemByProductId.get(product.getId());
            if (item != null && hasSnapshot(item)) {
                restoreSnapshot(product, item);
                restoredFromSnapshots = true;
            }
        }

        if (!restoredFromSnapshots) {
            BigDecimal inverseFactor = BigDecimal.ONE.divide(adjustment.getFactorApplied(), 12, RoundingMode.HALF_UP);
            PriceAdjustmentScope scope = adjustment.getScope() == null ? PriceAdjustmentScope.ALL : adjustment.getScope();
            for (Product product : products) {
                applyFactor(product, inverseFactor, scope);
            }
        }

        adjustment.setUndone(true);
        adjustment.setUndoneAt(LocalDateTime.now());
        adjustment.setUndoneBy(currentUsername());
    }

    @Override
    public String generateSystemBarcode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String barcode = nextSystemBarcodeCandidate();
            if (!productRepository.existsByBarcode(barcode)) {
                return barcode;
            }
        }
        throw new InvalidProductException("No se pudo generar un codigo de barras unico");
    }

    @Override
    public String renderBarcodeLabelSvg(String barcode) {
        return BARCODE_RENDERER.render(barcode);
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
        validateBarcode(product.getBarcode(), product.getId());
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

    private void saveManualAdjustmentIfNeeded(Product product, PriceSnapshot before) {
        PriceSnapshot after = PriceSnapshot.from(product);
        if (before.equalsPrices(after)) {
            return;
        }

        ProductPriceAdjustment adjustment = new ProductPriceAdjustment();
        adjustment.setAdjustmentType(PriceAdjustmentType.MANUAL);
        adjustment.setPercentage(null);
        adjustment.setFactorApplied(null);
        adjustment.setScope(PriceAdjustmentScope.ALL);
        adjustment.setProvider(product.getProvider());
        adjustment.setProductsAffected(1);
        adjustment.setCreatedBy(currentUsername());
        adjustment.setUndone(false);
        productPriceAdjustmentRepository.save(adjustment);

        ProductPriceAdjustmentItem item = new ProductPriceAdjustmentItem();
        item.setAdjustment(adjustment);
        item.setProduct(product);
        applySnapshots(item, before, after);
        productPriceAdjustmentItemRepository.save(item);
    }

    private void applySnapshots(ProductPriceAdjustmentItem item, PriceSnapshot before, PriceSnapshot after) {
        if (before == null || after == null) {
            return;
        }
        item.setPreviousCost(before.cost());
        item.setNewCost(after.cost());
        item.setPreviousPriceWholesaleNet(before.wholesaleNet());
        item.setNewPriceWholesaleNet(after.wholesaleNet());
        item.setPreviousPriceRetailNet(before.retailNet());
        item.setNewPriceRetailNet(after.retailNet());
    }

    private boolean hasSnapshot(ProductPriceAdjustmentItem item) {
        return item.getPreviousCost() != null
                || item.getPreviousPriceWholesaleNet() != null
                || item.getPreviousPriceRetailNet() != null;
    }

    private void restoreSnapshot(Product product, ProductPriceAdjustmentItem item) {
        if (item.getPreviousCost() != null) {
            product.setCost(item.getPreviousCost());
        }
        if (item.getPreviousPriceWholesaleNet() != null) {
            product.setPriceWholesaleNet(item.getPreviousPriceWholesaleNet());
        }
        if (item.getPreviousPriceRetailNet() != null) {
            product.setPriceRetailNet(item.getPreviousPriceRetailNet());
        }
        recalcPrices(product);
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

    private void validateBarcode(String barcode, Long productId) {
        if (barcode == null || barcode.isBlank()) {
            return;
        }
        String normalized = barcode.trim();
        if (!normalized.matches("^\\d{8,14}$")) {
            throw new InvalidProductException("El codigo de barras debe tener entre 8 y 14 digitos");
        }
        boolean exists = productId == null
                ? productRepository.existsByBarcode(normalized)
                : productRepository.existsByBarcodeAndIdNot(normalized, productId);
        if (exists) {
            throw new InvalidProductException("El codigo de barras ya pertenece a otro producto");
        }
    }

    private String nextSystemBarcodeCandidate() {
        long base = 200_000_000_000L + ThreadLocalRandom.current().nextLong(100_000_000_000L);
        String firstTwelve = String.valueOf(base).substring(0, 12);
        return firstTwelve + ean13CheckDigit(firstTwelve);
    }

    private int ean13CheckDigit(String firstTwelveDigits) {
        int sum = 0;
        for (int i = 0; i < firstTwelveDigits.length(); i++) {
            int digit = Character.digit(firstTwelveDigits.charAt(i), 10);
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10;
    }

    private record PriceSnapshot(BigDecimal cost, BigDecimal wholesaleNet, BigDecimal retailNet) {
        private static PriceSnapshot from(Product product) {
            return new PriceSnapshot(product.getCost(), product.getPriceWholesaleNet(), product.getPriceRetailNet());
        }

        private boolean equalsPrices(PriceSnapshot other) {
            if (other == null) {
                return false;
            }
            return equalsAmount(cost, other.cost)
                    && equalsAmount(wholesaleNet, other.wholesaleNet)
                    && equalsAmount(retailNet, other.retailNet);
        }

        private static boolean equalsAmount(BigDecimal left, BigDecimal right) {
            if (left == null && right == null) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            return left.compareTo(right) == 0;
        }
    }
}
