package com.sales.maidav.repository.quote;

import com.sales.maidav.model.quote.Quote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    @EntityGraph(attributePaths = {"seller"})
    @Query("select q from Quote q where q.seller.id = :sellerId or q.pinned = true "
            + "order by q.pinned desc, q.createdAt desc, q.id desc")
    List<Quote> findVisible(Long sellerId);

    @EntityGraph(attributePaths = {"seller"})
    Optional<Quote> findByIdAndPinnedTrue(Long id);

    @EntityGraph(attributePaths = {"seller"})
    List<Quote> findBySeller_IdOrderByCreatedAtDescIdDesc(Long sellerId);

    @EntityGraph(attributePaths = {"seller"})
    Optional<Quote> findByIdAndSeller_Id(Long id, Long sellerId);

    @Query(value = "select coalesce(max(id), 0) + 1 from quotes", nativeQuery = true)
    Long nextQuoteId();
}
