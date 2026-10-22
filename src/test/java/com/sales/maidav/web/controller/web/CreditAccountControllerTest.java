package com.sales.maidav.web.controller.web;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.sale.CreditAccount;
import com.sales.maidav.repository.sale.CreditInstallmentRepository;
import com.sales.maidav.repository.sale.CreditPaymentRepository;
import com.sales.maidav.repository.sale.SaleItemRepository;
import com.sales.maidav.service.sale.CreditAccountService;
import com.sales.maidav.service.settings.CompanySettingsService;
import com.sales.maidav.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditAccountControllerTest {

    @Mock
    private CreditAccountService creditAccountService;
    @Mock
    private CreditInstallmentRepository creditInstallmentRepository;
    @Mock
    private CreditPaymentRepository creditPaymentRepository;
    @Mock
    private CompanySettingsService companySettingsService;
    @Mock
    private SaleItemRepository saleItemRepository;
    @Mock
    private UserService userService;

    private CreditAccountController controller;

    @BeforeEach
    void setUp() {
        controller = new CreditAccountController(
                creditAccountService,
                creditInstallmentRepository,
                creditPaymentRepository,
                companySettingsService,
                saleItemRepository,
                userService
        );
    }

    @Test
    void listShowsAtMostFiveAccountsAndExposesNavigationMetadata() {
        List<CreditAccount> accounts = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(this::account)
                .toList();
        when(creditAccountService.findAll()).thenReturn(accounts);
        when(creditAccountService.getMoraWarning(anyLong())).thenReturn(null);
        when(creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(anyLong())).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.list(null, null, false, 0, null, model);

        assertThat((List<CreditAccount>) model.get("accounts"))
                .extracting(CreditAccount::getId)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat((List<?>) model.get("clientGroups")).hasSize(5);
        assertThat(view).isEqualTo("pages/accounts/index");
        assertThat(model.get("currentPage")).isEqualTo(0);
        assertThat(model.get("totalPages")).isEqualTo(2);
        assertThat(model.get("totalAccounts")).isEqualTo(7);
    }

    @Test
    void listReturnsTheRemainingAccountsOnTheNextPage() {
        List<CreditAccount> accounts = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(this::account)
                .toList();
        when(creditAccountService.findAll()).thenReturn(accounts);
        when(creditAccountService.getMoraWarning(anyLong())).thenReturn(null);
        when(creditInstallmentRepository.findByAccount_IdOrderByInstallmentNumber(anyLong())).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();

        controller.list(null, null, false, 1, null, model);

        assertThat((List<CreditAccount>) model.get("accounts"))
                .extracting(CreditAccount::getId)
                .containsExactly(6L, 7L);
        assertThat((List<?>) model.get("clientGroups")).hasSize(2);
        assertThat(model.get("currentPage")).isEqualTo(1);
    }

    private CreditAccount account(int id) {
        Client client = new Client();
        client.setId((long) id);
        client.setFirstName("Cliente");
        client.setLastName(String.format("%02d", id));
        client.setNationalId("DNI-" + id);

        CreditAccount account = new CreditAccount();
        account.setId((long) id);
        account.setClient(client);
        account.setBalance(BigDecimal.TEN);
        return account;
    }
}
