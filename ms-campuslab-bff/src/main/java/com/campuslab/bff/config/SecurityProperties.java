package com.campuslab.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades tipadas para la seguridad del BFF.
 * Se mapean desde el prefijo "campuslab.security" en application.yml.
 */
@ConfigurationProperties(prefix = "campuslab.security")
public class SecurityProperties {

    /**
     * Datos de la instancia de Azure AD (Microsoft Entra ID).
     */
    private Azure azure = new Azure();

    /**
     * Nombre del claim del JWT que contiene los roles del usuario.
     * Azure AD entrega los "App Roles" en el claim "roles" por defecto.
     */
    private String rolesClaim = "roles";

    public Azure getAzure() {
        return azure;
    }

    public void setAzure(Azure azure) {
        this.azure = azure;
    }

    public String getRolesClaim() {
        return rolesClaim;
    }

    public void setRolesClaim(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    public static class Azure {
        /**
         * Tenant ID (Directory ID) de Azure AD.
         */
        private String tenantId;

        /**
         * Audience esperada en el claim "aud" del JWT: puede ser el Application
         * (client) ID de la app registrada, o el Application ID URI (api://...)
         * si la API se expuso con un identificador personalizado.
         */
        private String audience;

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }
}
