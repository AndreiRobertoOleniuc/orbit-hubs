package com.orbit.security;

import com.orbit.app.auth.UserProvisioningService;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs after successful bearer-token authentication and refreshes the local
 * {@code users} row from the verified Clerk claims.
 */
class UserProvisioningFilter extends OncePerRequestFilter {

    private final UserProvisioningService provisioningService;

    UserProvisioningFilter(UserProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            provisioningService.provision(jwtAuth.getToken());
        }

        filterChain.doFilter(request, response);
    }

}
