package com.sales.maidav.service.product;

import com.sales.maidav.model.product.Product;
import com.sales.maidav.repository.product.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

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
        if (product.getVatRate() == null || (product.getVatRate() != 0 && product.getVatRate() != 21)) {
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
                .add(BigDecimal.valueOf(product.getVatRate()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        product.setPriceWholesale(
                product.getPriceWholesaleNet().multiply(vatMultiplier).setScale(2, RoundingMode.HALF_UP)
        );
        product.setPriceRetail(
                product.getPriceRetailNet().multiply(vatMultiplier).setScale(2, RoundingMode.HALF_UP)
        );
    }
}
