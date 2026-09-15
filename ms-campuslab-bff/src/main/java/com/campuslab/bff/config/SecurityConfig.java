package com.campuslab.bff.config;

import com.campuslab.bff.security.AudienceValidator;
import com.campuslab.bff.security.AzureAdJwtAuthenticationConverter;
import com.campuslab.bff.security.RestAccessDeniedHandler;
import com.campuslab.bff.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Configuración central de seguridad del BFF.
 *
 * <p>El microservicio se despliega detrás de un AWS API Gateway y actúa como
 * OAuth2 Resource Server: no emite ni gestiona sesiones, únicamente valida
 * los JWT (access tokens) emitidos por Azure AD (Microsoft Entra ID) que
 * llegan en el header {@code Authorization: Bearer <token>}.</p>
 *
 * <p>Validaciones aplicadas sobre cada JWT:</p>
 * <ul>
 *   <li>Firma criptográfica: verificada contra las claves públicas (JWKS)
 *       publicadas por Azure AD para el tenant configurado.</li>
 *   <li>Vigencia (exp/nbf): {@link JwtValidators#createDefaultWithIssuer} agrega
 *       un {@code JwtTimestampValidator}.</li>
 *   <li>Issuer (iss): debe coincidir exactamente con el issuer-uri configurado.</li>
 *   <li>Audience (aud): validado por {@link AudienceValidator} contra el
 *       Client ID / Application ID URI de esta API.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // habilita @PreAuthorize en los controllers
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${campuslab.security.azure.audience}")
    private String expectedAudience;

    private final AzureAdJwtAuthenticationConverter azureAdJwtAuthenticationConverter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    public SecurityConfig(AzureAdJwtAuthenticationConverter azureAdJwtAuthenticationConverter,
                           RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                           RestAccessDeniedHandler restAccessDeniedHandler) {
        this.azureAdJwtAuthenticationConverter = azureAdJwtAuthenticationConverter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
    }

    /**
     * Cadena de filtros de seguridad HTTP. Define qué rutas son públicas,
     * cuáles requieren autenticación y qué rol se exige para cada grupo de
     * endpoints. Al ser un BFF puramente API (sin vistas server-side), se
     * deshabilitan CSRF y el manejo de sesión HTTP (stateless).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // API stateless protegida por Bearer JWT: no aplica CSRF de formularios.
            .csrf(csrf -> csrf.disable())

            // Sin sesión HTTP: cada petición se autentica de forma independiente
            // mediante el JWT entregado por Azure AD.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(authorize -> authorize
                // Endpoints públicos: health checks consumidos por el API Gateway / ALB.
                .requestMatchers(new AntPathRequestMatcher("/actuator/health/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/actuator/info")).permitAll()

                // --- Autorización por rol de negocio ---
                // Ruta de prueba para verificar rapidamente la validacion de JWT +
                // rol ADMIN (ver SampleController#adminPing).
                .requestMatchers(new AntPathRequestMatcher("/api/admin/**")).hasRole("ADMIN")

                // Administración de laboratorio: solo Admin.
                .requestMatchers(new AntPathRequestMatcher("/api/v1/admin/**")).hasRole("ADMIN")

                // Operación/mantenimiento de equipos de laboratorio: Admin y Tecnico.
                .requestMatchers(new AntPathRequestMatcher("/api/v1/equipos/**")).hasAnyRole("ADMIN", "TECNICO")

                // Reservas y prácticas de laboratorio: cualquier rol autenticado
                // del dominio académico, incluido el Estudiante.
                .requestMatchers(HttpMethod.GET, "/api/v1/reservas/**")
                    .hasAnyRole("ADMIN", "TECNICO", "ESTUDIANTE")
                .requestMatchers(HttpMethod.POST, "/api/v1/reservas/**")
                    .hasAnyRole("ADMIN", "ESTUDIANTE")

                // Reportes y trazabilidad: Admin y Auditor.
                .requestMatchers(new AntPathRequestMatcher("/api/v1/auditoria/**")).hasAnyRole("ADMIN", "AUDITOR")

                // --- Rutas reenviadas por el gateway a los microservicios de dominio ---
                // Catalogo (ms-campuslab-catalog): consulta abierta a cualquier rol
                // academico; alta y modificacion de stock/cupo reservada a Admin/Tecnico.
                .requestMatchers(HttpMethod.GET, "/api/catalog/**")
                    .hasAnyRole("ADMIN", "TECNICO", "ESTUDIANTE", "DOCENTE")
                .requestMatchers(new AntPathRequestMatcher("/api/catalog/**")).hasAnyRole("ADMIN", "TECNICO")

                // Reservas (ms-campuslab-bookings): mismo criterio que las rutas de
                // referencia /api/v1/reservas/** de mas arriba.
                .requestMatchers(HttpMethod.GET, "/api/bookings/**")
                    .hasAnyRole("ADMIN", "TECNICO", "ESTUDIANTE", "DOCENTE")
                .requestMatchers(HttpMethod.POST, "/api/bookings/**")
                    .hasAnyRole("ADMIN", "ESTUDIANTE")
                .requestMatchers(HttpMethod.PUT, "/api/bookings/**")
                    .hasAnyRole("ADMIN", "TECNICO", "ESTUDIANTE", "DOCENTE")

                // Cualquier otra ruta bajo /api requiere, al menos, un JWT válido.
                .requestMatchers(new AntPathRequestMatcher("/api/**")).authenticated()

                // Todo lo demás: denegado por defecto (fail-closed).
                .anyRequest().denyAll()
            )

            // Resource Server: valida el Bearer JWT en cada petición y construye
            // el Authentication a partir del conversor personalizado de roles.
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(azureAdJwtAuthenticationConverter)
                )
                // 401 para JWT ausente/inválido, 403 para JWT válido sin el rol requerido.
                .authenticationEntryPoint(restAuthenticationEntryPoint)
                .accessDeniedHandler(restAccessDeniedHandler)
            )

            // El AccessDeniedHandler anterior cubre el filtro de recursos OAuth2;
            // se registra también a nivel general para cualquier AccessDeniedException
            // que pueda originarse fuera del filtro Bearer (p. ej. @PreAuthorize).
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(restAuthenticationEntryPoint)
                .accessDeniedHandler(restAccessDeniedHandler)
            );

        return http.build();
    }

    /**
     * Construye el {@link JwtDecoder} a partir del issuer de Azure AD,
     * combinando las validaciones por defecto (issuer + expiración/vigencia,
     * y verificación de firma vía JWKS) con la validación estricta de audience.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // JwtDecoders.fromIssuerLocation realiza el OIDC Discovery contra
        // {issuerUri}/.well-known/openid-configuration, obtiene el JWKS URI
        // y configura la verificación criptográfica de la firma (RS256) más
        // el validador de "iss" por defecto.
        NimbusJwtDecoder nimbusJwtDecoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> defaultValidators = JwtValidators.createDefaultWithIssuer(issuerUri); // iss + exp/nbf
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(expectedAudience);
        OAuth2TokenValidator<Jwt> combinedValidator =
                new DelegatingOAuth2TokenValidator<>(defaultValidators, audienceValidator);

        nimbusJwtDecoder.setJwtValidator(combinedValidator);
        return nimbusJwtDecoder;
    }
}
