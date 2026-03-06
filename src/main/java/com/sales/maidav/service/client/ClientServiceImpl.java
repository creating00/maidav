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
        return clientRepository.findWithRelationsById(id)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    @Override
    public Client create(Client client) {
        normalizeAndValidate(client);
        String nationalId = client.getNationalId();
        validateNationalId(nationalId);
        if (clientRepository.existsByNationalId(nationalId)) {
            throw new DuplicateNationalIdException("DNI o CUIT ya existente");
        }
        return clientRepository.save(client);
    }

    @Override
    public Client update(Long id, Client data) {
        normalizeAndValidate(data);
        String nationalId = data.getNationalId();
        validateNationalId(nationalId);
        if (clientRepository.existsByNationalIdAndIdNot(nationalId, id)) {
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
        if (!nationalId.matches("^(\\d{8}|\\d{11})$")) {
            throw new InvalidNationalIdException("DNI debe tener 8 digitos o CUIT 11 digitos");
        }
    }

    private void normalizeAndValidate(Client client) {
        client.setNationalId(trimToNull(client.getNationalId()));
        client.setFirstName(trimToNull(client.getFirstName()));
        client.setLastName(trimToNull(client.getLastName()));
        client.setPhone(trimToNull(client.getPhone()));
        client.setAddress(trimToNull(client.getAddress()));
        client.setEmail(trimToNull(client.getEmail()));
        client.setObservations(trimToNull(client.getObservations()));

        if (client.getZone() != null && client.getZone().getId() == null) {
            client.setZone(null);
        }
        if (client.getSeller() != null && client.getSeller().getId() == null) {
            client.setSeller(null);
        }
        if (client.getRecommendedBy() != null && client.getRecommendedBy().getId() == null) {
            client.setRecommendedBy(null);
        }

        if (client.getNationalId() == null) {
            throw new InvalidClientException("DNI o CUIT es obligatorio");
        }
        if (client.getFirstName() == null) {
            throw new InvalidClientException("El nombre es obligatorio");
        }
        if (client.getLastName() == null) {
            throw new InvalidClientException("El apellido es obligatorio");
        }
        if (client.getSeller() == null || client.getSeller().getId() == null) {
            throw new InvalidClientException("Debe seleccionar un vendedor asignado");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
