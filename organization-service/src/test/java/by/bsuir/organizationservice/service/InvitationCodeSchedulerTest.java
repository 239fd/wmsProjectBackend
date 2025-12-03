package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.repository.OrganizationInvitationCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationCodeScheduler Unit Tests")
class InvitationCodeSchedulerTest {

    @Mock
    private OrganizationInvitationCodeRepository invitationCodeRepository;

    @InjectMocks
    private InvitationCodeScheduler invitationCodeScheduler;

    @Test
    @DisplayName("deactivateExpiredCodes: Should deactivate expired codes")
    void deactivateExpiredCodes_ShouldDeactivateExpiredCodes() {
        when(invitationCodeRepository.deactivateExpiredCodes(any(LocalDateTime.class)))
                .thenReturn(5);

        invitationCodeScheduler.deactivateExpiredCodes();

        verify(invitationCodeRepository).deactivateExpiredCodes(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("deactivateExpiredCodes: Given no expired codes Should not log anything")
    void deactivateExpiredCodes_GivenNoExpiredCodes_ShouldNotLogAnything() {
        when(invitationCodeRepository.deactivateExpiredCodes(any(LocalDateTime.class)))
                .thenReturn(0);

        invitationCodeScheduler.deactivateExpiredCodes();

        verify(invitationCodeRepository).deactivateExpiredCodes(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("deactivateExpiredCodes: Should be called with current time")
    void deactivateExpiredCodes_ShouldBeCalledWithCurrentTime() {
        when(invitationCodeRepository.deactivateExpiredCodes(any(LocalDateTime.class)))
                .thenReturn(3);

        invitationCodeScheduler.deactivateExpiredCodes();

        verify(invitationCodeRepository, times(1)).deactivateExpiredCodes(any(LocalDateTime.class));
    }
}

