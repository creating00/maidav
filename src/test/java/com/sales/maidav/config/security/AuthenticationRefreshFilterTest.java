package com.sales.maidav.config.security;

import com.sales.maidav.service.user.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationRefreshFilterTest {

    private final UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);
    private final FilterChain filterChain = mock(FilterChain.class);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshesAuthoritiesOnlyOnceWithinTheSessionInterval() throws Exception {
        AuthenticationRefreshFilter filter = new AuthenticationRefreshFilter(userDetailsService, Duration.ofMinutes(5));
        MockHttpSession session = new MockHttpSession();
        authenticateAs("seller@maidav.com", "SALES_READ");
        when(userDetailsService.loadUserByUsername("seller@maidav.com"))
                .thenReturn(userDetails("seller@maidav.com", "SALES_READ"));

        filter.doFilter(request(session, "/sales"), new MockHttpServletResponse(), filterChain);
        filter.doFilter(request(session, "/sales"), new MockHttpServletResponse(), filterChain);

        verify(userDetailsService, times(1)).loadUserByUsername("seller@maidav.com");
        verify(filterChain, times(2)).doFilter(any(), any());
    }

    @Test
    void doesNotRefreshAuthoritiesForStaticResources() throws Exception {
        AuthenticationRefreshFilter filter = new AuthenticationRefreshFilter(userDetailsService, Duration.ofMinutes(5));
        authenticateAs("seller@maidav.com", "SALES_READ");

        filter.doFilter(request(new MockHttpSession(), "/css/app.css"), new MockHttpServletResponse(), filterChain);

        verify(userDetailsService, never()).loadUserByUsername("seller@maidav.com");
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void replacesAuthoritiesWhenTheRefreshDetectsPermissionChanges() throws Exception {
        AuthenticationRefreshFilter filter = new AuthenticationRefreshFilter(userDetailsService, Duration.ofMinutes(5));
        authenticateAs("seller@maidav.com", "SALES_READ");
        when(userDetailsService.loadUserByUsername("seller@maidav.com"))
                .thenReturn(userDetails("seller@maidav.com", "SALES_CREATE"));

        filter.doFilter(request(new MockHttpSession(), "/sales"), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("SALES_CREATE");
    }

    private void authenticateAs(String email, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        email,
                        "secret",
                        List.of(new SimpleGrantedAuthority(authority))
                )
        );
    }

    private User userDetails(String email, String authority) {
        return new User(email, "secret", List.of(new SimpleGrantedAuthority(authority)));
    }

    private MockHttpServletRequest request(MockHttpSession session, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setSession(session);
        return request;
    }
}
