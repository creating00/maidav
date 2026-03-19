package com.sales.maidav.config.security;

import com.sales.maidav.service.user.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Component
public class AuthenticationRefreshFilter extends OncePerRequestFilter {

    private final UserDetailsServiceImpl userDetailsService;

    public AuthenticationRefreshFilter(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
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
        filterChain.doFilter(request, response);
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
