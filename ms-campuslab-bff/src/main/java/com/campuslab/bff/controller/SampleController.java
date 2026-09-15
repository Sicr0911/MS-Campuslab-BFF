package com.campuslab.bff.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints de referencia para verificar rápidamente el comportamiento de
 * autenticación (401), autorización por rol (403) y extracción de claims.
 * No representan lógica de negocio real de CampusLab.
 */
@RestController
public class SampleController {

    @GetMapping("/api/v1/reservas/mias")
    public Map<String, Object> misReservas(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        return Map.of(
                "usuario", jwt.getClaimAsString("preferred_username"),
                "roles", authentication.getAuthorities(),
                "mensaje", "Acceso concedido a reservas propias."
        );
    }

    @GetMapping("/api/v1/admin/ping")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> adminPing() {
        return Map.of("mensaje", "Acceso concedido: rol ADMIN verificado.");
    }

    @GetMapping("/api/admin/ping")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> adminPingV2() {
        return Map.of("mensaje", "Acceso concedido: rol ADMIN verificado (ruta /api/admin).");
    }

    @GetMapping("/api/v1/auditoria/reportes")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public Map<String, String> reportesAuditoria() {
        return Map.of("mensaje", "Acceso concedido: rol ADMIN o AUDITOR verificado.");
    }
}
