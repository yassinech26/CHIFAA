package com.chifaa.backend.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI chifaaApi() {
        return new OpenAPI().info(new Info().title("CHIFAA E3").version("0.1"))
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes("bearer",
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")));
    }

    @Bean
    OpenApiCustomizer securedOperations() {
        return api -> api.getPaths().forEach((path, item) -> {
            if (path.startsWith("/api/chat/") || path.startsWith("/api/admin/") || path.equals("/api/auth/logout")) {
                item.readOperations().forEach(operation -> {
                    operation.addSecurityItem(new SecurityRequirement().addList("bearer"));
                    operation.getResponses().addApiResponse("401",
                        new io.swagger.v3.oas.models.responses.ApiResponse().description("Authentication required or expired"));
                    operation.getResponses().addApiResponse("403",
                        new io.swagger.v3.oas.models.responses.ApiResponse().description("Access denied"));
                });
            }
        });
    }
}
