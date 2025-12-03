package by.bsuir.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("sso-api", r -> r
                        .path("/api/auth/**", "/api/profile/**", "/api/oauth/**")
                        .uri("lb://SSOSERVICE"))
                .route("organization-api", r -> r
                        .path("/api/organizations/**")
                        .uri("lb://ORGANIZATION-SERVICE"))
                .route("warehouse-api", r -> r
                        .path("/api/warehouses/**", "/api/racks/**")
                        .uri("lb://WAREHOUSE-SERVICE"))
                .route("product-api", r -> r
                        .path("/api/products/**", "/api/batches/**", "/api/operations/**",
                              "/api/inventory/**", "/api/inventory-check/**", "/api/analytics/**")
                        .uri("lb://PRODUCT-SERVICE"))
                .route("document-api", r -> r
                        .path("/api/documents/**")
                        .uri("lb://DOCUMENT-SERVICE"))
                .route("warehouse-service", r -> r
                        .path("/warehouse-service/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://WAREHOUSE-SERVICE"))
                .route("organization-service", r -> r
                        .path("/organization-service/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://ORGANIZATION-SERVICE"))
                .route("sso-service", r -> r
                        .path("/sso-service/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://SSOSERVICE"))
                .route("product-service", r -> r
                        .path("/product-service/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://PRODUCT-SERVICE"))
                .route("document-service", r -> r
                        .path("/document-service/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://DOCUMENT-SERVICE"))
                .build();
    }
}
