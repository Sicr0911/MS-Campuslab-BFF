package com.campuslab.bff.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Valida que el claim "aud" (audience) del JWT contenga el identificador
 * de esta API (Application/Client ID o Application ID URI registrado en
 * Azure AD). Sin esta validación, un token válido emitido para OTRA API
 * dentro del mismo tenant también sería aceptado por este microservicio,
 * lo cual es una vulnerabilidad conocida (confusión de audiencia).
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE_ERROR = new OAuth2Error(
            "invalid_token",
            "El token no contiene la audiencia (aud) esperada para este recurso.",
            null
    );

    private final String expectedAudience;

    public AudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getAudience() != null && jwt.getAudience().contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE_ERROR);
    }
}
