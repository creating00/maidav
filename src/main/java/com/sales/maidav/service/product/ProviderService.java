package com.sales.maidav.service.product;

import com.sales.maidav.model.product.Provider;

import java.util.List;

public interface ProviderService {
    List<Provider> findAll();
    Provider findById(Long id);
    Provider create(Provider provider);
    Provider update(Long id, Provider provider);
    void delete(Long id);
}
