package by.bsuir.productservice.service;

import by.bsuir.productservice.model.entity.ReceiptSession;
import by.bsuir.productservice.model.enums.ReceiptSessionStatus;
import by.bsuir.productservice.repository.ReceiptSessionRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptSessionCleanupService {

    private final ReceiptSessionRepository receiptSessionRepository;

    @Value("${receipt-session.stale-ttl-hours:48}")
    private long staleTtlHours;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireStaleSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(staleTtlHours);
        List<ReceiptSession> stale =
                receiptSessionRepository.findByStatusAndCreatedAtBefore(ReceiptSessionStatus.PAUSED, cutoff);
        if (stale.isEmpty()) {
            return;
        }
        for (ReceiptSession session : stale) {
            session.setStatus(ReceiptSessionStatus.EXPIRED);
            session.setCompletedAt(LocalDateTime.now());
        }
        receiptSessionRepository.saveAll(stale);
        log.info("Receipt-session cleanup: помечено EXPIRED {} зависших PAUSED-сессий (старше {} ч)",
                stale.size(), staleTtlHours);
    }
}
