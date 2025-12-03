package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.repository.OrganizationInvitationCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationCodeScheduler {

    private final OrganizationInvitationCodeRepository invitationCodeRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deactivateExpiredCodes() {
        log.info("Running scheduled task: deactivating expired invitation codes");

        int deactivated = invitationCodeRepository.deactivateExpiredCodes(LocalDateTime.now());

        if (deactivated > 0) {
            log.info("Deactivated {} expired invitation codes", deactivated);
        }
    }
}
