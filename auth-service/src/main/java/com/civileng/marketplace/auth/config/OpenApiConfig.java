package com.civileng.marketplace.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Civil Engineering Marketplace - Auth Service API",
                version = "1.0.0",
                description = "Authentication and Authorization Service for " +
                        "Civil Engineering Marketplace Platform",
                contact = @Contact(
                        name = "Civil Engineering Marketplace",
                        email = "support@civilengineer.com",
                        url = "https://civilengineer.com"),
                license = @License(
                        name = "Proprietary",
                        url = "https://civilengineer.com/license")),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local Development"),
                @Server(url = "https://api.civilengineer.com", description = "Production")
        },
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT Bearer Token Authentication")
public class OpenApiConfig {
}
