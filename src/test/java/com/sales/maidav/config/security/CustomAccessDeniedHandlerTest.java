package com.sales.maidav.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAccessDeniedHandlerTest {

    @Test
    void forwardsToAccessDeniedWithForbiddenStatusInsteadOfRedirecting() throws Exception {
        CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sales/8/void");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getForwardedUrl()).isEqualTo("/access-denied");
        assertThat(request.getAttribute("accessDeniedMessage"))
                .isEqualTo("No tienes permiso para anular la venta");
    }
}
