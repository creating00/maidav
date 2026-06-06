package com.sales.maidav.service.client;

import com.sales.maidav.model.client.Client;
import com.sales.maidav.model.user.User;
import com.sales.maidav.repository.client.ClientRepository;
import com.sales.maidav.repository.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClientServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN")
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAllowsSevenDigitDni() {
        Client client = validClient("1234567");
        when(clientRepository.save(client)).thenReturn(client);

        Client saved = service.create(client);

        assertThat(saved.getNationalId()).isEqualTo("1234567");
        verify(clientRepository).existsByNationalId("1234567");
        verify(clientRepository).save(client);
    }

    @Test
    void createRejectsUnsupportedNationalIdLength() {
        Client client = validClient("123456");

        assertThatThrownBy(() -> service.create(client))
                .isInstanceOf(InvalidNationalIdException.class)
                .hasMessage("DNI debe tener 7 u 8 digitos o CUIT 11 digitos");
    }

    private Client validClient(String nationalId) {
        User seller = new User();
        seller.setId(1L);

        Client client = new Client();
        client.setNationalId(nationalId);
        client.setFirstName("Ana");
        client.setLastName("Perez");
        client.setSeller(seller);
        return client;
    }
}
