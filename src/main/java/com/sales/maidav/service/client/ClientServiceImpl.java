package com.sales.maidav.service.client;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.repository.client.ClientRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;

    @Override
    public List<Client> findAll() {
        return clientRepository.findAll();
    }

    @Override
    public Client findById(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    @Override
    public Client create(Client client) {
        String nationalId = client.getNationalId();
        validateNationalId(nationalId);
        if (nationalId != null && clientRepository.existsByNationalId(nationalId)) {
            throw new DuplicateNationalIdException("DNI o CUIT ya existente");
        }
        return clientRepository.save(client);
    }

    @Override
    public Client update(Long id, Client data) {
        String nationalId = data.getNationalId();
        validateNationalId(nationalId);
        if (nationalId != null && clientRepository.existsByNationalIdAndIdNot(nationalId, id)) {
            throw new DuplicateNationalIdException("DNI o CUIT ya existente");
        }
        Client client = findById(id);

        client.setNationalId(data.getNationalId());
        client.setFirstName(data.getFirstName());
        client.setLastName(data.getLastName());
        client.setPhone(data.getPhone());
        client.setAddress(data.getAddress());
        client.setZone(data.getZone());
        client.setEmail(data.getEmail());
        client.setBirthDate(data.getBirthDate());
        client.setObservations(data.getObservations());
        client.setSeller(data.getSeller());
        client.setRecommendedBy(data.getRecommendedBy());

        return client;
    }

    @Override
    public void delete(Long id) {
        clientRepository.deleteById(id);
    }

    @Override
    public List<Client> search(String term) {
        if (term == null || term.isBlank()) {
            return clientRepository.findAll();
        }

        return clientRepository
                .findByNationalIdContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                        term, term, term
                );
    }

    @Override
    public long count() {
        return clientRepository.count();
    }

    private void validateNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) {
            return;
        }
        if (!nationalId.matches("^(\\d{8}|\\d{11})$")) {
            throw new InvalidNationalIdException("DNI debe tener 8 dígitos o CUIT 11 dígitos");
        }
    }
}
