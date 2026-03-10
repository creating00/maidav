package com.sales.maidav.repository.client;

import com.sales.maidav.model.client.Client;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    @EntityGraph(attributePaths = {"zone", "seller", "recommendedBy"})
    List<Client> findAll();

    List<Client> findByNationalIdContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String nationalId,
            String firstName,
            String lastName
    );

    @EntityGraph(attributePaths = {"zone", "seller", "recommendedBy"})
    List<Client> findBySeller_Id(Long sellerId);

    @EntityGraph(attributePaths = {"zone", "seller", "recommendedBy"})
    List<Client> findBySeller_IdAndNationalIdContainingIgnoreCaseOrSeller_IdAndFirstNameContainingIgnoreCaseOrSeller_IdAndLastNameContainingIgnoreCase(
            Long sellerIdForNationalId,
            String nationalId,
            Long sellerIdForFirstName,
            String firstName,
            Long sellerIdForLastName,
            String lastName
    );

    boolean existsByNationalId(String nationalId);

    boolean existsByNationalIdAndIdNot(String nationalId, Long id);

    @EntityGraph(attributePaths = {"zone", "seller", "recommendedBy"})
    Optional<Client> findWithRelationsById(Long id);

    @EntityGraph(attributePaths = {"zone", "seller", "recommendedBy"})
    Optional<Client> findWithRelationsByIdAndSeller_Id(Long id, Long sellerId);
}

