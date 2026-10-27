package com.campuslab.bff;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Matriz completa rol x ruta reenviada por el gateway (SecurityConfig).
 *
 * <p>El token lleva el claim "roles" exactamente como lo emite el App
 * Registration de Azure AD ("Admin", "Tecnico", "Estudiante", "Auditor"),
 * asi que pasa por el AzureAdJwtAuthenticationConverter real: lo unico
 * simulado es la verificacion de firma (JwtDecoder).</p>
 *
 * <p>Si la regla deja pasar, el BFF intenta reenviar a un microservicio que
 * no esta levantado (puerto 1): eso da 5xx o una excepcion, pero nunca
 * 401/403, que es lo unico que interesa verificar aqui.</p>
 */
@SpringBootTest(properties = {
        "campuslab.services.catalog-base-url=http://localhost:1",
        "campuslab.services.bookings-base-url=http://localhost:1",
        "campuslab.services.audit-base-url=http://localhost:1",
        "campuslab.services.report-base-url=http://localhost:1",
        "campuslab.services.rabbitmq-admin-base-url=http://localhost:1",
        "campuslab.services.kafka-admin-base-url=http://localhost:1"
})
@AutoConfigureMockMvc
class MatrizDeRolesTest {

    /** Valores de los App Roles tal como estan en Azure AD. */
    private static final List<String> ROLES_AZURE = List.of("Admin", "Tecnico", "Estudiante", "Auditor");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void tokensConLaFormaDeAzure() {
        // "token-Tecnico" -> roles=["Tecnico"]; "token-Tecnico+Auditor" -> dos roles; "token-" -> sin roles.
        // (un Bearer no admite comas ni tildes: "token-Tecnico_acento" -> roles=["Técnico"])
        when(jwtDecoder.decode(anyString())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            String roles = token.substring("token-".length()).replace("Tecnico_acento", "Técnico");
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("sub-" + roles)
                    .claim("preferred_username", roles.toLowerCase() + "@campuslab.cl")
                    .claim("roles", roles.isEmpty() ? List.of() : List.of(roles.split("\\+")))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        });
    }

    @ParameterizedTest(name = "{0} {1} -> permitido a {2}")
    @CsvSource(delimiter = ';', value = {
            "GET;    /api/catalog/resources;           Admin Tecnico Estudiante",
            "POST;   /api/catalog/resources;           Admin Tecnico",
            "PUT;    /api/catalog/resources/1;         Admin Tecnico",
            "GET;    /api/bookings;                    Admin Tecnico Estudiante",
            "POST;   /api/bookings;                    Admin Estudiante",
            "PUT;    /api/bookings/1/status;           Admin Tecnico Estudiante",
            "GET;    /api/audit;                       Admin Auditor",
            "GET;    /api/audit/resumen;               Admin Auditor",
            "GET;    /api/audit/reservas/1;            Admin Auditor",
            "POST;   /api/audit/solicitudes;           Admin Auditor",
            "PUT;    /api/audit/solicitudes/1/respuesta; Admin Auditor",
            "GET;    /api/report/kpis;                 Admin",
            "GET;    /api/report/top-resources;        Admin",
            "GET;    /api/admin/rabbitmq/queues;       Admin",
            "DELETE; /api/admin/rabbitmq/queues/x;     Admin",
            "GET;    /api/admin/kafka/topics;          Admin"
    })
    void cadaRolDeAzureRecibeLoQueDiceSecurityConfig(String metodo, String ruta, String permitidos) {
        Set<String> rolesPermitidos = Set.of(permitidos.trim().split("\\s+"));

        for (String rol : ROLES_AZURE) {
            int status = ejecutar(metodo, ruta, "token-" + rol);
            if (rolesPermitidos.contains(rol)) {
                assertThat(status).as("%s %s con rol %s deberia pasar el BFF", metodo, ruta, rol)
                        .isNotIn(401, 403);
            } else {
                assertThat(status).as("%s %s con rol %s deberia ser 403", metodo, ruta, rol)
                        .isEqualTo(403);
            }
        }
    }

    @Test
    void usuarioSinRolAsignadoEnAzure_recibe403EnTodo() {
        assertThat(ejecutar("GET", "/api/catalog/resources", "token-")).isEqualTo(403);
        assertThat(ejecutar("GET", "/api/bookings", "token-")).isEqualTo(403);
        assertThat(ejecutar("GET", "/api/audit", "token-")).isEqualTo(403);
    }

    @Test
    void usuarioConDosRoles_sumaLosPermisosDeAmbos() {
        assertThat(ejecutar("GET", "/api/audit", "token-Tecnico+Auditor")).isNotIn(401, 403);
        assertThat(ejecutar("POST", "/api/catalog/resources", "token-Tecnico+Auditor")).isNotIn(401, 403);
        assertThat(ejecutar("GET", "/api/report/kpis", "token-Tecnico+Auditor")).isEqualTo(403);
    }

    @Test
    void rolConAcento_seNormalizaIgual() {
        assertThat(ejecutar("PUT", "/api/catalog/resources/1", "token-Tecnico_acento")).isNotIn(401, 403);
    }

    @Test
    void sinToken_responde401() {
        try {
            int status = mockMvc.perform(request(HttpMethod.GET, "/api/bookings"))
                    .andReturn().getResponse().getStatus();
            assertThat(status).isEqualTo(401);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    /** Devuelve el status, o 502 si el reenvio al microservicio (inexistente) lanza una excepcion. */
    private int ejecutar(String metodo, String ruta, String token) {
        try {
            return mockMvc.perform(request(HttpMethod.valueOf(metodo), ruta)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andReturn().getResponse().getStatus();
        } catch (Exception ex) {
            return 502;
        }
    }
}
