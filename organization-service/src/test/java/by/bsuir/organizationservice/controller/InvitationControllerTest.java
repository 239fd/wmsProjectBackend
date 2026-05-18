package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.CreateInvitationRequest;
import by.bsuir.organizationservice.dto.InvitationResponse;
import by.bsuir.organizationservice.service.InvitationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationController Tests")
class InvitationControllerTest {

    @Mock
    private InvitationService invitationService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private InvitationController controller;

    private InvitationResponse sample() {
        return new InvitationResponse(
                UUID.randomUUID(), UUID.randomUUID(), "i@i.by", "WORKER",
                UUID.randomUUID(), "http://link", LocalDateTime.now(),
                LocalDateTime.now().plusDays(7), false, true, null);
    }

    @Test
    @DisplayName("createInvitation: 201 Created с InvitationResponse")
    void createInvitation_whenCalled_thenReturns201() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateInvitationRequest req = new CreateInvitationRequest("i@i.by", "WORKER", null);
        when(authentication.getName()).thenReturn(userId.toString());
        when(invitationService.createInvitation(orgId, req, userId)).thenReturn(sample());

        ResponseEntity<InvitationResponse> response =
                controller.createInvitation(orgId, req, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("getOrganizationInvitations: 200 OK со списком")
    void getOrganizationInvitations_whenCalled_thenReturns200() {
        UUID orgId = UUID.randomUUID();
        when(invitationService.getOrganizationInvitations(orgId)).thenReturn(List.of(sample()));

        ResponseEntity<List<InvitationResponse>> response =
                controller.getOrganizationInvitations(orgId, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }
}
