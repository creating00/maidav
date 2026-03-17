package com.sales.maidav.service.dashboard;

import com.sales.maidav.model.client.Zone;
import com.sales.maidav.model.sale.InstallmentStatus;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.sale.CreditAccountRepository;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class DashboardPortfolioService {

    private final CreditAccountRepository creditAccountRepository;
    private final CreditInstallmentRepository creditInstallmentRepository;
    private final CreditPaymentRepository creditPaymentRepository;
    private final UserRepository userRepository;

    public DashboardPortfolioService(CreditAccountRepository creditAccountRepository,
                                     CreditInstallmentRepository creditInstallmentRepository,
                                     CreditPaymentRepository creditPaymentRepository,
                                     UserRepository userRepository) {
        this.creditAccountRepository = creditAccountRepository;
        this.creditInstallmentRepository = creditInstallmentRepository;
        this.creditPaymentRepository = creditPaymentRepository;
        this.userRepository = userRepository;
    }

    public DashboardPortfolioSnapshot buildSnapshot(Long requestedSellerId, Long requestedZoneId) {
        boolean adminView = isCurrentUserAdmin();
        Long currentSellerId = currentUserId();
        Long effectiveSellerId;
        if (adminView) {
            effectiveSellerId = normalizeFilterId(requestedSellerId);
        } else {
            effectiveSellerId = currentSellerId == null ? Long.valueOf(-1L) : currentSellerId;
        }
        Long effectiveZoneId = normalizeFilterId(requestedZoneId);
        LocalDate cutoffDate = LocalDate.now();

        BigDecimal totalFinanced = creditAccountRepository.sumTotalAmountByFilters(effectiveSellerId, effectiveZoneId);
        BigDecimal expectedCollected = creditInstallmentRepository.sumScheduledAmountDueUpTo(cutoffDate, effectiveSellerId, effectiveZoneId);
        BigDecimal collectedAmount = creditPaymentRepository.sumPaidAmountUpTo(cutoffDate, effectiveSellerId, effectiveZoneId);
        BigDecimal overdueDebt = creditInstallmentRepository.sumOverdueOutstandingByFilters(
                cutoffDate,
                List.of(InstallmentStatus.PENDING, InstallmentStatus.PARTIAL),
                effectiveSellerId,
                effectiveZoneId
        );
        BigDecimal pendingBalance = creditAccountRepository.sumBalanceByFilters(effectiveSellerId, effectiveZoneId);
        long creditsCount = creditAccountRepository.countByFilters(effectiveSellerId, effectiveZoneId);

        return new DashboardPortfolioSnapshot(
                cutoffDate,
                creditsCount,
                totalFinanced,
                expectedCollected,
                collectedAmount,
                overdueDebt,
                pendingBalance,
                adminView ? effectiveSellerId : currentSellerId,
                effectiveZoneId,
                resolveSellerLabel(adminView, currentSellerId, effectiveSellerId),
                resolveZoneLabel(effectiveZoneId),
                adminView
        );
    }

    public List<DashboardFilterOption> getSellerOptions() {
        if (!isCurrentUserAdmin()) {
            Long sellerId = currentUserId();
            if (sellerId == null) {
                return List.of();
            }
            return userRepository.findById(sellerId)
                    .map(user -> List.of(new DashboardFilterOption(user.getId(), formatSellerLabel(user))))
                    .orElse(List.of());
        }

        return creditAccountRepository.findDistinctSellersWithCredits()
                .stream()
                .sorted(Comparator.comparing(this::formatSellerLabel, String.CASE_INSENSITIVE_ORDER))
                .map(user -> new DashboardFilterOption(user.getId(), formatSellerLabel(user)))
                .toList();
    }

    public List<DashboardFilterOption> getZoneOptions() {
        return creditAccountRepository.findDistinctZonesWithCredits()
                .stream()
                .sorted(Comparator.comparing(this::formatZoneLabel, String.CASE_INSENSITIVE_ORDER))
                .map(zone -> new DashboardFilterOption(zone.getId(), formatZoneLabel(zone)))
                .toList();
    }

    public boolean isCurrentUserAdminView() {
        return isCurrentUserAdmin();
    }

    private Long normalizeFilterId(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private String resolveSellerLabel(boolean adminView, Long currentSellerId, Long effectiveSellerId) {
        if (!adminView) {
            return userRepository.findById(currentSellerId == null ? -1L : currentSellerId)
                    .map(this::formatSellerLabel)
                    .orElse("Mi cartera");
        }
        if (effectiveSellerId == null) {
            return "Todos los vendedores";
        }
        return userRepository.findById(effectiveSellerId)
                .map(this::formatSellerLabel)
                .orElse("Vendedor seleccionado");
    }

    private String resolveZoneLabel(Long effectiveZoneId) {
        if (effectiveZoneId == null) {
            return "Todas las zonas";
        }
        return creditAccountRepository.findDistinctZonesWithCredits()
                .stream()
                .filter(zone -> effectiveZoneId.equals(zone.getId()))
                .findFirst()
                .map(this::formatZoneLabel)
                .orElse("Zona seleccionada");
    }

    private String formatSellerLabel(User user) {
        if (user == null) {
            return "-";
        }
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? user.getEmail() : fullName;
    }

    private String formatZoneLabel(Zone zone) {
        if (zone == null) {
            return "-";
        }
        String address = zone.getAddress() == null ? "" : zone.getAddress().trim();
        String number = zone.getNumber() == null ? "" : zone.getNumber().trim();
        String label = (address + " " + number).trim();
        return label.isEmpty() ? "Zona #" + zone.getId() : label;
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
                .map(User::getId)
                .orElse(null);
    }
}
