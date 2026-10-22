package com.sales.maidav.config.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        String uri = request.getRequestURI();
        String message = "No tienes acceso";

        if (uri != null && uri.contains("/clients/") && uri.endsWith("/delete")) {
            message = "No tienes permiso para eliminar el cliente";
        }
        if (uri != null && uri.contains("/sales/") && uri.endsWith("/void")) {
            message = "No tienes permiso para anular la venta";
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        request.setAttribute("accessDeniedMessage", message);
        request.getRequestDispatcher("/access-denied").forward(request, response);
    }
}
