package com.campuslab.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Enrutamiento del BFF hacia los microservicios de dominio.
 *
 * <p>El BFF no implementa la logica de negocio de catalogo ni de reservas:
 * unicamente valida el JWT (ver SecurityConfig) y reenvia la peticion, tal
 * cual, al microservicio correspondiente segun el prefijo de la ruta.</p>
 */
@Configuration
public class GatewayConfig {

    @Bean
    public RouterFunction<ServerResponse> catalogRoute(
            @Value("${campuslab.services.catalog-base-url}") String catalogBaseUrl) {
        return route("catalog_service")
                .route(path("/api/catalog/**"), http(catalogBaseUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> bookingsRoute(
            @Value("${campuslab.services.bookings-base-url}") String bookingsBaseUrl) {
        return route("bookings_service")
                .route(path("/api/bookings/**"), http(bookingsBaseUrl))
                .build();
    }
}
