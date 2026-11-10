package com.sales.maidav.web.controller.web;

import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.service.export.CreditAccountExportService;
import com.sales.maidav.service.sale.CreditAccountService;
import com.sales.maidav.service.settings.CompanySettingsService;
import com.sales.maidav.service.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(CreditAccountExportSecurityTest.Config.class)
class CreditAccountExportSecurityTest {
    @Autowired CreditAccountController controller;
    @Autowired CreditAccountExportService exportService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniesExportToNonAdminBeforeReadingData() {
        var authentication = new UsernamePasswordAuthenticationToken("collector@example.com", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_COBRADOR")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> controller.export(null, null, authentication))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(exportService);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean CreditAccountExportService exportService() { return mock(CreditAccountExportService.class); }
        @Bean CreditAccountController controller(CreditAccountExportService exportService) {
            return new CreditAccountController(mock(CreditAccountService.class),
                    mock(CreditInstallmentRepository.class), mock(CreditPaymentRepository.class),
                    mock(CompanySettingsService.class), mock(SaleItemRepository.class),
                    mock(UserService.class), exportService);
        }
    }
}
