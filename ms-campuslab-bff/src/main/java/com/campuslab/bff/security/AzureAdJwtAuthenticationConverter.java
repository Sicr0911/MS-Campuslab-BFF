package com.campuslab.bff.security;

import com.campuslab.bff.config.SecurityProperties;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Convierte un {@link Jwt} emitido por Azure AD (Microsoft Entra ID) en un
 * {@link AbstractAuthenticationToken} de Spring Security, extrayendo los
 * "App Roles" del claim configurado (por defecto "roles") y exponiéndolos
 * como {@link GrantedAuthority} con el prefijo estándar "ROLE_".
 *
 * <p>Roles de negocio soportados: Admin, Tecnico, Estudiante, Auditor.
 * Se normalizan (sin acentos, mayúsculas) para evitar discrepancias entre
 * lo configurado en el App Registration de Azure AD y las anotaciones
 * {@code @PreAuthorize}/{@code hasRole(...)} del microservicio.</p>
 */
@Component
public class AzureAdJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private final SecurityProperties securityProperties;

    public AzureAdJwtAuthenticationConverter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractRoles(jwt).stream()
                .map(this::normalizeRoleName)
                .map(role -> ROLE_PREFIX + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet())
                .stream()
                .collect(Collectors.toList());

        // "sub" es el identificador único del usuario en Azure AD (Object ID);
        // se usa como principal name del token de autenticación.
        String principalClaim = jwt.hasClaim("preferred_username") ? "preferred_username" : "sub";

        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString(principalClaim));
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Jwt jwt) {
        String claimName = securityProperties.getRolesClaim();
        Object rawRoles = jwt.getClaims().get(claimName);

        if (rawRoles instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        // Azure AD normalmente entrega "roles" como array, pero se contempla
        // el caso de un único rol entregado como string plano.
        if (rawRoles instanceof String single && !single.isBlank()) {
            return List.of(single);
        }

        return List.of();
    }

    /**
     * Normaliza el nombre de un rol: elimina acentos/diacríticos y lo pasa a
     * mayúsculas, de forma que "Técnico" y "Tecnico" resuelvan al mismo
     * GrantedAuthority ("ROLE_TECNICO").
     */
    private String normalizeRoleName(String role) {
        String normalized = Normalizer.normalize(role.trim(), Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        return normalized.toUpperCase(Locale.ROOT);
    }
}
