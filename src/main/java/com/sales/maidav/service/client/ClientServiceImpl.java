package com.sales.maidav.service.client;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.repository.client.ClientRepository;
import com.sales.maidav.repository.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    @Override
    public List<Client> findAll() {
        if (isCurrentUserAdmin()) {
            return clientRepository.findAll();
        }
        Long sellerId = currentUserId();
        return sellerId == null ? List.of() : clientRepository.findBySeller_Id(sellerId);
    }

    @Override
    public Client findById(Long id) {
        if (isCurrentUserAdmin()) {
            return clientRepository.findWithRelationsById(id)
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        }
        Long sellerId = currentUserId();
        return clientRepository.findWithRelationsByIdAndSeller_Id(id, sellerId == null ? -1L : sellerId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
    }

    @Override
    public Client create(Client client) {
        assignSellerIfNeeded(client);
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
        assignSellerIfNeeded(data);
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
        findById(id);
        clientRepository.deleteById(id);
    }

    @Override
    public List<Client> search(String term) {
        if (term == null || term.isBlank()) {
            return findAll();
        }

        if (isCurrentUserAdmin()) {
            return clientRepository
                    .findByNationalIdContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                            term, term, term
                    );
        }
        Long sellerId = currentUserId();
        if (sellerId == null) {
            return List.of();
        }
        return clientRepository
                .findBySeller_IdAndNationalIdContainingIgnoreCaseOrSeller_IdAndFirstNameContainingIgnoreCaseOrSeller_IdAndLastNameContainingIgnoreCase(
                        sellerId, term, sellerId, term, sellerId, term
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

    private void assignSellerIfNeeded(Client client) {
        if (isCurrentUserAdmin()) {
            return;
        }
        Long sellerId = currentUserId();
        if (sellerId == null) {
            throw new InvalidClientException("No se pudo resolver el vendedor actual");
        }
        client.setSeller(userRepository.findById(sellerId)
                .orElseThrow(() -> new InvalidClientException("Vendedor no encontrado")));
    }

    private boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
