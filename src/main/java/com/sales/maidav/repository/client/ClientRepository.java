package com.sales.maidav.repository.client;

import com.sales.maidav.model.client.Client;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    @EntityGraph(attributePaths = {"zone", "seller", "recommendedBy"})
    List<Client> findAll();

    @Query(value = """
            select c.id from clients c
            where lower(unaccent(c.national_id)) like :term
               or lower(unaccent(c.first_name)) like :term
               or lower(unaccent(c.last_name)) like :term
            """, nativeQuery = true)
    List<Long> searchIdsByTerm(@Param("term") String term);

    @EntityGraph(attributePaths = {"zone", "seller", "recommendedBy"})
    List<Client> findBySeller_Id(Long sellerId);

    @Query(value = """
            select c.id from clients c
            where c.seller_id = :sellerId
              and (lower(unaccent(c.national_id)) like :term
                   or lower(unaccent(c.first_name)) like :term
                   or lower(unaccent(c.last_name)) like :term)
            """, nativeQuery = true)
    List<Long> searchIdsBySellerIdAndTerm(@Param("sellerId") Long sellerId, @Param("term") String term);

    @EntityGraph(attributePaths = {"zone", "seller", "recommendedBy"})
    List<Client> findByIdIn(List<Long> ids);

    boolean existsByNationalId(String nationalId);

    boolean existsByNationalIdAndIdNot(String nationalId, Long id);

    @EntityGraph(attributePaths = {"zone", "seller", "recommendedBy"})
    Optional<Client> findWithRelationsById(Long id);

    @EntityGraph(attributePaths = {"zone", "seller", "recommendedBy"})
    Optional<Client> findWithRelationsByIdAndSeller_Id(Long id, Long sellerId);

    long countBySeller_Id(Long sellerId);
}

