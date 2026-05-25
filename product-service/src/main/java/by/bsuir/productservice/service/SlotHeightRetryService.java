package by.bsuir.productservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotHeightRetryService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long INITIAL_DELAY_MS = 30_000L;

    private final RestTemplate loadBalancedRestTemplate;
    private final ConcurrentLinkedQueue<PendingAdjustment> queue = new ConcurrentLinkedQueue<>();

    public void enqueue(UUID slotId, BigDecimal deltaCm) {
        if (slotId == null || deltaCm == null || deltaCm.signum() == 0) return;
        queue.add(new PendingAdjustment(slotId, deltaCm, new AtomicInteger(0),
                Instant.now().plusMillis(INITIAL_DELAY_MS)));
        log.warn("Slot height adjust queued for retry: slot={}, delta={} (queue size={})",
                slotId, deltaCm, queue.size());
    }

    public int queueSize() {
        return queue.size();
    }

    @Scheduled(fixedDelay = 30_000L, initialDelay = 60_000L)
    public void processQueue() {
        if (queue.isEmpty()) return;
        Instant now = Instant.now();
        int processed = 0;
        int succeeded = 0;
        int failed = 0;
        int dropped = 0;

        int initialSize = queue.size();
        for (int i = 0; i < initialSize; i++) {
            PendingAdjustment p = queue.poll();
            if (p == null) break;
            processed++;

            if (now.isBefore(p.nextAttemptAt())) {
                queue.add(p);
                continue;
            }

            boolean ok = tryAdjust(p.slotId(), p.deltaCm());
            if (ok) {
                succeeded++;
                log.info("Slot height retry succeeded: slot={}, delta={}, attempts={}",
                        p.slotId(), p.deltaCm(), p.attempts().get() + 1);
                continue;
            }

            int attempts = p.attempts().incrementAndGet();
            if (attempts >= MAX_ATTEMPTS) {
                dropped++;
                log.error("Slot height retry GIVING UP after {} attempts: slot={}, delta={}. "
                                + "DRIFT — потребуется ручной пересчёт remaining_height_cm.",
                        attempts, p.slotId(), p.deltaCm());
                continue;
            }
            failed++;
            long backoffMs = INITIAL_DELAY_MS * (1L << Math.min(attempts, 5));
            queue.add(new PendingAdjustment(p.slotId(), p.deltaCm(), p.attempts(),
                    now.plusMillis(backoffMs)));
        }

        if (processed > 0) {
            log.info("Slot height retry tick: processed={}, succeeded={}, failed={}, dropped={}, remaining={}",
                    processed, succeeded, failed, dropped, queue.size());
        }
    }

    private boolean tryAdjust(UUID slotId, BigDecimal deltaCm) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of("delta", deltaCm);
            loadBalancedRestTemplate.exchange(
                    "http://WAREHOUSE-SERVICE/api/internal/slots/" + slotId + "/height",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return true;
        } catch (Exception e) {
            log.debug("Slot height retry attempt failed: slot={}, delta={}, error={}",
                    slotId, deltaCm, e.getMessage());
            return false;
        }
    }

    private record PendingAdjustment(
            UUID slotId,
            BigDecimal deltaCm,
            AtomicInteger attempts,
            Instant nextAttemptAt) {}
}
