package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.ValidateInvitationResponse;
import by.bsuir.organizationservice.service.InvitationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicInvitationController Tests")
class PublicInvitationControllerTest {

    @Mock
    private InvitationService invitationService;

    @InjectMocks
    private PublicInvitationController controller;

    @Test
    @DisplayName("validateInvitation: 200 OK с результатом валидации")
    void validateInvitation_whenCalled_thenReturns200() {
        UUID token = UUID.randomUUID();
        ValidateInvitationResponse expected = new ValidateInvitationResponse(
                true, "ОрГ", "WORKER", "i@i.by", "ok");
        when(invitationService.validateInvitation(token)).thenReturn(expected);

        ResponseEntity<ValidateInvitationResponse> response = controller.validateInvitation(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().valid()).isTrue();
    }

    @Test
    @DisplayName("markAsUsed: парсит userId из body и делегирует, 200 OK с message")
    void markAsUsed_whenCalled_thenDelegates() {
        UUID token = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ResponseEntity<Map<String, String>> response =
                controller.markAsUsed(token, Map.of("userId", userId.toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        verify(invitationService).markAsUsed(token, userId);
    }
}
