package com.campuslab.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Carga el contexto completo del BFF y verifica las reglas de autorizacion
 * de SecurityConfig. El JwtDecoder real hace OIDC discovery contra Azure AD
 * al crearse, asi que se reemplaza por un mock: los tests no salen a
 * Internet y el token se inyecta con jwt() de spring-security-test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CampuslabBffApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void contextLoads_healthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void sinToken_responde401() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_accedeARutaDeAdministracion() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void estudiante_noAccedeAAdministracionDeRabbitMq() throws Exception {
        // la regla /api/admin/** corta antes de reenviar a ms-campuslab-rabbitmq-admin
        mockMvc.perform(get("/api/admin/rabbitmq/queues")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ESTUDIANTE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditor_accedeAAuditoriaPeroNoAReportes() throws Exception {
        mockMvc.perform(get("/api/v1/auditoria/reportes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/report/kpis")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rutaFueraDeApi_seDeniegaPorDefecto() throws Exception {
        mockMvc.perform(get("/cualquier-cosa")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }
}
