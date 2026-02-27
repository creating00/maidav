package com.sales.maidav.service.product;

import com.sales.maidav.model.product.Provider;
import com.sales.maidav.repository.product.ProviderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProviderServiceImpl implements ProviderService {

    private final ProviderRepository providerRepository;

    @Override
    public List<Provider> findAll() {
        return providerRepository.findAll();
    }

    @Override
    public Provider findById(Long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
    }

    @Override
    public Provider create(Provider provider) {
        if (provider.getName() == null || provider.getName().isBlank()) {
            throw new InvalidProductException("El nombre del proveedor es obligatorio");
        }
        return providerRepository.save(provider);
    }

    @Override
    public Provider update(Long id, Provider data) {
        Provider provider = findById(id);
        provider.setName(data.getName());
        provider.setContactName(data.getContactName());
        provider.setPhone(data.getPhone());
        provider.setEmail(data.getEmail());
        provider.setAddress(data.getAddress());
        return provider;
    }

    @Override
    public void delete(Long id) {
        providerRepository.deleteById(id);
    }
}
