package com.sales.maidav.service.product;

import com.sales.maidav.model.product.Product;

import java.util.List;

public interface ProductService {
    List<Product> findAll();
    Product findById(Long id);
    Product create(Product product);
    Product update(Long id, Product product);
    void delete(Long id);
    long count();
    long countLowStock();
    List<Product> findLowStock();
}
