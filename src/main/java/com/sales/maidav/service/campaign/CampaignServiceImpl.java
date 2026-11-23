package com.sales.maidav.service.campaign;

import com.sales.maidav.model.campaign.*;
import com.sales.maidav.repository.campaign.*;
import com.sales.maidav.repository.product.ProductRepository;
import com.sales.maidav.repository.user.UserRepository;
import com.sales.maidav.web.dto.CampaignForm;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignServiceImpl implements CampaignService {
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");
    private final CampaignRepository repository;
    private final CampaignItemRepository itemRepository;
    private final CampaignIdempotencyKeyRepository keyRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Campaign create(CampaignForm form, Long userId) {
        String key;
        try {
            if (userId == null || form.getOperationKey() == null || form.getOperationKey().length() != 36) throw new IllegalArgumentException();
            key = UUID.fromString(form.getOperationKey()).toString();
        } catch (IllegalArgumentException ex) { throw new InvalidCampaignException("Abra un formulario nuevo para registrar la campaña"); }
        keyRepository.insertIfAbsent(userId, key);
        var request = keyRepository.findForUpdate(userId, key).orElseThrow();
        if (request.getCampaign() != null) return request.getCampaign();
        if (form.getName() == null || form.getName().isBlank() || form.getName().length() > 150) throw new InvalidCampaignException("Ingrese un nombre de hasta 150 caracteres");
        if (form.getDescription() != null && form.getDescription().length() > 2000) throw new InvalidCampaignException("La descripción admite hasta 2000 caracteres");
        if (form.getStartDate() == null || form.getEndDate() == null || form.getStartDate().getYear() < 1 || form.getEndDate().getYear() > 9999 || form.getEndDate().isBefore(form.getStartDate())) throw new InvalidCampaignException("Ingrese fechas válidas: fin no puede ser anterior a inicio");
        if (form.getItems() == null || form.getItems().isEmpty() || form.getItems().size() > 100) throw new InvalidCampaignException("Agregue entre 1 y 100 productos");
        Campaign campaign = new Campaign();
        campaign.setName(form.getName().trim());
        campaign.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
        campaign.setStartDate(form.getStartDate());
        campaign.setEndDate(form.getEndDate());
        campaign.setRegisteredBy(userRepository.findById(userId).orElseThrow(() -> new InvalidCampaignException("Usuario no encontrado")));
        Set<Long> seen = new HashSet<>();
        for (CampaignForm.Item input : form.getItems()) {
            if (input == null || input.getProductId() == null || input.getDiscountMode() == null || !seen.add(input.getProductId())) throw new InvalidCampaignException("Seleccione productos sin repetir y el tipo de oferta");
            var product = productRepository.findById(input.getProductId()).orElseThrow(() -> new InvalidCampaignException("Producto no encontrado"));
            BigDecimal normal = product.getPriceRetail();
            validatePrice(normal);
            BigDecimal value = input.getDiscountValue();
            validatePrice(value);
            BigDecimal offer;
            if (input.getDiscountMode() == DiscountMode.PERCENTAGE) {
                if (value.compareTo(new BigDecimal("100")) >= 0) throw new InvalidCampaignException("El descuento debe ser mayor a 0 y menor a 100%");
                offer = normal.multiply(BigDecimal.ONE.subtract(value.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
            } else offer = value.setScale(2);
            if (offer.signum() <= 0 || offer.compareTo(normal) >= 0) throw new InvalidCampaignException("El precio de oferta debe ser positivo y menor al precio minorista normal");
            CampaignItem item = new CampaignItem();
            item.setCampaign(campaign);
            item.setProduct(product);
            item.setProductCode(product.getProductCode());
            item.setProductDescription(product.getDescription());
            item.setImagePath(product.getImagePath());
            item.setNormalPrice(normal.setScale(2));
            item.setOfferPrice(offer);
            item.setDiscountMode(input.getDiscountMode());
            item.setDiscountValue(value.setScale(2));
            campaign.getItems().add(item);
        }
        campaign.setItemCount(campaign.getItems().size());
        repository.save(campaign);
        request.setCampaign(campaign);
        return campaign;
    }

    @Override
    public Campaign findById(Long id) {
        return repository.findDetail(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
    }

    @Override
    public Page<Campaign> findPage(CampaignFilter state, int page) {
        LocalDate today = LocalDate.now();
        return repository.findAll((root, query, cb) -> switch (state == null ? CampaignFilter.ACTIVE : state) {
            case ALL -> cb.conjunction();
            case CANCELLED -> cb.isTrue(root.get("cancelled"));
            case ACTIVE -> cb.and(cb.isFalse(root.get("cancelled")), cb.lessThanOrEqualTo(root.get("startDate"), today), cb.greaterThanOrEqualTo(root.get("endDate"), today));
            case FUTURE -> cb.and(cb.isFalse(root.get("cancelled")), cb.greaterThan(root.get("startDate"), today));
            case EXPIRED -> cb.and(cb.isFalse(root.get("cancelled")), cb.lessThan(root.get("endDate"), today));
        }, PageRequest.of(Math.max(0, page), 20, Sort.by(Sort.Direction.DESC, "startDate", "id")));
    }

    @Override
    public CampaignOffer findActiveOffer(Long itemId) { return activeOffer(itemId, false); }

    @Override
    public CampaignOffer findOffer(Long itemId) {
        var item = itemRepository.findById(itemId).orElseThrow(() -> new InvalidCampaignException("Oferta no encontrada"));
        if (item.getProduct() == null) throw new InvalidCampaignException("El producto ya no está disponible");
        return new CampaignOffer(item);
    }

    @Override
    @Transactional
    public CampaignOffer lockActiveOffer(Long itemId) { return activeOffer(itemId, true); }

    private CampaignOffer activeOffer(Long itemId, boolean lock) {
        CampaignItem item = itemRepository.findById(itemId).orElseThrow(() -> new InvalidCampaignException("Oferta no encontrada"));
        Campaign campaign = lock ? repository.lockForSale(item.getCampaign().getId()).orElseThrow() : item.getCampaign();
        if (campaign.getState() != CampaignFilter.ACTIVE) throw new InvalidCampaignException("La campaña no está activa. Consulte las ofertas disponibles");
        if (item.getProduct() == null) throw new InvalidCampaignException("El producto ya no está disponible");
        return new CampaignOffer(item);
    }

    @Override
    public List<CampaignOffer> findActiveHighlights() {
        return itemRepository.findActive(LocalDate.now(), PageRequest.of(0, 3)).stream().map(CampaignOffer::new).toList();
    }

    @Override
    @Transactional
    public void cancel(Long id, Long userId) {
        Campaign campaign = repository.lockForCancellation(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaña no encontrada"));
        if (campaign.isCancelled()) return;
        campaign.setCancelled(true);
        campaign.setCancelledAt(LocalDateTime.now());
        campaign.setCancelledBy(userRepository.findById(userId).orElseThrow(() -> new InvalidCampaignException("Usuario no encontrado")));
    }

    private void validatePrice(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX_PRICE) > 0 || value.stripTrailingZeros().scale() > 2) throw new InvalidCampaignException("Ingrese importes positivos con hasta dos decimales y hasta $" + MAX_PRICE);
    }
}
