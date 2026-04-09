package com.sales.maidav.repository.quote;

import com.sales.maidav.model.quote.QuoteItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteItemRepository extends JpaRepository<QuoteItem, Long> {

    @EntityGraph(attributePaths = {"quote", "product", "product.provider"})
    List<QuoteItem> findByQuote_IdOrderByIdAsc(Long quoteId);
}
