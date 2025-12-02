package by.bsuir.apigateway.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;





@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingMetricsFilter implements GlobalFilter, Ordered {

    private final MeterRegistry meterRegistry;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();
        long startTime = System.currentTimeMillis();

        log.info("Incoming request: {} {} from {}",
                method,
                path,
                exchange.getRequest().getRemoteAddress());

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    long duration = System.currentTimeMillis() - startTime;
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();

                    if (statusCode != null) {
                        log.info("Completed request: {} {} - Status: {} - Duration: {}ms",
                                method, path, statusCode.value(), duration);


                        recordMetrics(method, path, statusCode.value(), duration);
                    }
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;

                    log.error("Failed request: {} {} - Error: {} - Duration: {}ms",
                            method, path, error.getMessage(), duration);


                    recordMetrics(method, path, 500, duration);
                });
    }

    private void recordMetrics(String method, String path, int statusCode, long duration) {

        Counter.builder("gateway.requests.total")
                .tag("method", method)
                .tag("path", sanitizePath(path))
                .tag("status", String.valueOf(statusCode))
                .register(meterRegistry)
                .increment();


        Timer.builder("gateway.requests.duration")
                .tag("method", method)
                .tag("path", sanitizePath(path))
                .register(meterRegistry)
                .record(Duration.ofMillis(duration));
    }

    private String sanitizePath(String path) {

        return path.replaceAll("/[0-9a-f-]{36}", "/{id}")
                   .replaceAll("/\\d+", "/{id}");
    }

    @Override
    public int getOrder() {
        return -99;
    }
}
