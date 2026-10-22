package com.sales.maidav.config.security;

import com.sales.maidav.service.user.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Component
public class AuthenticationRefreshFilter extends OncePerRequestFilter {

    private static final String LAST_REFRESH_ATTRIBUTE =
            AuthenticationRefreshFilter.class.getName() + ".lastRefreshAt";

    private final UserDetailsServiceImpl userDetailsService;
    private final Duration refreshInterval;

    public AuthenticationRefreshFilter(UserDetailsServiceImpl userDetailsService,
                                       @Value("${app.security.authority-refresh-interval:PT5M}")
                                       Duration refreshInterval) {
        this.userDetailsService = userDetailsService;
        this.refreshInterval = refreshInterval;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getName() != null
                && !authentication.getName().isBlank()) {
            refreshAuthenticationWhenDue(request, authentication);
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/app.css")
                || path.equals("/favicon.ico")
                || path.equals("/favicon.svg")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/uploads/");
    }

    private void refreshAuthenticationWhenDue(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            refreshAuthentication(authentication);
            return;
        }

        synchronized (session) {
            long now = System.currentTimeMillis();
            Object lastRefresh = session.getAttribute(LAST_REFRESH_ATTRIBUTE);
            if (lastRefresh instanceof Long refreshedAt && now - refreshedAt < refreshInterval.toMillis()) {
                return;
            }
            refreshAuthentication(authentication);
            session.setAttribute(LAST_REFRESH_ATTRIBUTE, now);
        }
    }

    private void refreshAuthentication(Authentication authentication) {
        UserDetails refreshedUser = userDetailsService.loadUserByUsername(authentication.getName());
        if (!sameAuthorities(authentication.getAuthorities(), refreshedUser.getAuthorities())) {
            UsernamePasswordAuthenticationToken refreshedAuthentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            refreshedUser,
                            authentication.getCredentials(),
                            refreshedUser.getAuthorities()
                    );
            refreshedAuthentication.setDetails(authentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(refreshedAuthentication);
        }
    }

    private boolean sameAuthorities(Iterable<? extends GrantedAuthority> current,
                                    Iterable<? extends GrantedAuthority> refreshed) {
        Set<String> currentNames = new HashSet<>();
        for (GrantedAuthority authority : current) {
            currentNames.add(authority.getAuthority());
        }
        Set<String> refreshedNames = new HashSet<>();
        for (GrantedAuthority authority : refreshed) {
            refreshedNames.add(authority.getAuthority());
        }
        return currentNames.equals(refreshedNames);
    }
}
