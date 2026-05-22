package by.bsuir.productservice.service;

import by.bsuir.productservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCleanupService {

    private final InventoryRepository inventoryRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupOnStartup() {
        runCleanup("startup");
    }

    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void scheduledCleanup() {
        runCleanup("scheduled");
    }

    private void runCleanup(String trigger) {
        int empty = inventoryRepository.deleteEmptyInventory();
        int orphans = inventoryRepository.deleteOrphanedInventoryWithoutCell();
        if (empty > 0 || orphans > 0) {
            log.info("Inventory cleanup ({}): empty={} (qty<=0), orphans={} (cellId=null, no reserve)",
                    trigger, empty, orphans);
        }
    }
}
