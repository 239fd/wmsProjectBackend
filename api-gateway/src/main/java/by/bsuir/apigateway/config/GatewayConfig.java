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

                .route("warehouse-service", r -> r
                        .path("/warehouse-service/**")
                        .filters(f -> f
                                .stripPrefix(1)
                        )
                        .uri("lb://WAREHOUSE-SERVICE"))


                .route("organization-service", r -> r
                        .path("/organization-service/**")
                        .filters(f -> f
                                .stripPrefix(1)
                        )
                        .uri("lb://ORGANIZATION-SERVICE"))


                .route("sso-service", r -> r
                        .path("/sso-service/**")
                        .filters(f -> f
                                .stripPrefix(1)
                        )
                        .uri("lb://SSOSERVICE"))


                .route("product-service", r -> r
                        .path("/product-service/**")
                        .filters(f -> f
                                .stripPrefix(1)
                        )
                        .uri("lb://PRODUCT-SERVICE"))


                .route("document-service", r -> r
                        .path("/document-service/**")
                        .filters(f -> f
                                .stripPrefix(1)
                        )
                        .uri("lb://DOCUMENT-SERVICE"))

                .build();
    }
}
