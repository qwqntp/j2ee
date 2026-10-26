package com.j2ee.tour.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI tourBookingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tour Booking API")
                        .version("v1")
                        .description("""
                                Public tour catalogue, JWT authentication and admin account endpoints.
                                Send access tokens as `Authorization: Bearer <token>`.
                                The refresh token is an HttpOnly cookie scoped to `/api/auth`.
                                Booking and payment endpoints are planned, not implemented yet.
                                """))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("15-minute access token from login, register or refresh.")));
    }
}
