package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.request.CreateOrganizationRequest;
import by.bsuir.organizationservice.dto.request.UpdateOrganizationRequest;
import by.bsuir.organizationservice.dto.response.InvitationCodeResponse;
import by.bsuir.organizationservice.dto.response.OrganizationDumpResponse;
import by.bsuir.organizationservice.dto.response.OrganizationResponse;
import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import by.bsuir.organizationservice.service.OrganizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationController Tests")
class OrganizationControllerTest {

    @Mock
    private OrganizationService organizationService;

    @InjectMocks
    private OrganizationController controller;

    private OrganizationResponse sample(UUID orgId) {
        return new OrganizationResponse(
                orgId, "Test", "T", "100234567", "Минск",
                OrganizationStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("createOrganization: DIRECTOR → 201 Created")
    void createOrganization_givenDirectorRole_whenCalled_thenReturns201() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CreateOrganizationRequest req = new CreateOrganizationRequest(
                "Test Co", "TC", "100234567", "Минск");
        when(organizationService.createOrganization(req, userId)).thenReturn(sample(orgId));

        ResponseEntity<OrganizationResponse> response = controller.createOrganization(req, userId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("createOrganization: не DIRECTOR → 403")
    void createOrganization_givenWorkerRole_whenCalled_thenForbidden() {
        CreateOrganizationRequest req = new CreateOrganizationRequest("X", null, "100234567", null);

        ResponseEntity<OrganizationResponse> response = controller.createOrganization(req, UUID.randomUUID(), "WORKER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("createOrganization: userId не определён → 401")
    void createOrganization_givenNoUserId_whenCalled_thenUnauthorized() {
        CreateOrganizationRequest req = new CreateOrganizationRequest("X", null, "100234567", null);

        ResponseEntity<OrganizationResponse> response = controller.createOrganization(req, null, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getOrganization: 200 OK")
    void getOrganization_givenExisting_whenCalled_thenReturns200() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getOrganization(orgId)).thenReturn(sample(orgId));

        assertThat(controller.getOrganization(orgId).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getAllOrganizations: без status → все")
    void getAllOrganizations_givenNoStatus_whenCalled_thenAll() {
        when(organizationService.getAllOrganizations()).thenReturn(List.of(sample(UUID.randomUUID())));

        ResponseEntity<List<OrganizationResponse>> response = controller.getAllOrganizations(null);

        assertThat(response.getBody()).hasSize(1);
        verify(organizationService).getAllOrganizations();
    }

    @Test
    @DisplayName("getAllOrganizations: с status → фильтр")
    void getAllOrganizations_givenStatus_whenCalled_thenFilters() {
        when(organizationService.getOrganizationsByStatus(OrganizationStatus.ACTIVE))
                .thenReturn(List.of(sample(UUID.randomUUID())));

        controller.getAllOrganizations(OrganizationStatus.ACTIVE);

        verify(organizationService).getOrganizationsByStatus(OrganizationStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateOrganization: DIRECTOR → 200 OK")
    void updateOrganization_givenDirectorRole_whenCalled_thenReturns200() {
        UUID orgId = UUID.randomUUID();
        UpdateOrganizationRequest req = new UpdateOrganizationRequest("New", null, null, null);
        when(organizationService.updateOrganization(orgId, req)).thenReturn(sample(orgId));

        assertThat(controller.updateOrganization(orgId, req, "DIRECTOR").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("updateOrganization: ACCOUNTANT → 200 OK")
    void updateOrganization_givenAccountantRole_whenCalled_thenReturns200() {
        UUID orgId = UUID.randomUUID();
        UpdateOrganizationRequest req = new UpdateOrganizationRequest("New", null, null, null);
        when(organizationService.updateOrganization(orgId, req)).thenReturn(sample(orgId));

        assertThat(controller.updateOrganization(orgId, req, "ACCOUNTANT").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("updateOrganization: WORKER → 403")
    void updateOrganization_givenWorkerRole_whenCalled_thenForbidden() {
        UpdateOrganizationRequest req = new UpdateOrganizationRequest("X", null, null, null);

        assertThat(controller.updateOrganization(UUID.randomUUID(), req, "WORKER").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("deleteOrganization: DIRECTOR → 200 OK с dump")
    void deleteOrganization_givenDirectorRole_whenCalled_thenReturns200() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrganizationDumpResponse dump = new OrganizationDumpResponse("ok", Map.of(), "ts");
        when(organizationService.deleteOrganization(orgId, userId)).thenReturn(dump);

        assertThat(controller.deleteOrganization(orgId, userId, "DIRECTOR").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("deleteOrganization: WORKER → 403")
    void deleteOrganization_givenWorkerRole_whenCalled_thenForbidden() {
        assertThat(controller.deleteOrganization(UUID.randomUUID(), UUID.randomUUID(), "WORKER")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("generateInvitationCodes: DIRECTOR → 200 OK")
    void generateInvitationCodes_givenDirectorRole_whenCalled_thenReturns200() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.generateInvitationCodes(orgId))
                .thenReturn(List.of(new InvitationCodeResponse("code-1", UUID.randomUUID(), "wh", LocalDateTime.now())));

        assertThat(controller.generateInvitationCodes(orgId, "DIRECTOR").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("generateInvitationCodes: WORKER → 403")
    void generateInvitationCodes_givenWorkerRole_whenCalled_thenForbidden() {
        assertThat(controller.generateInvitationCodes(UUID.randomUUID(), "WORKER")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("regenerateInvitationCode: DIRECTOR → 200 OK")
    void regenerateInvitationCode_givenDirectorRole_whenCalled_thenReturns200() {
        UUID orgId = UUID.randomUUID();
        UUID whId = UUID.randomUUID();
        when(organizationService.regenerateInvitationCodeForWarehouse(orgId, whId))
                .thenReturn(new InvitationCodeResponse("code-2", whId, "wh", LocalDateTime.now()));

        assertThat(controller.regenerateInvitationCode(orgId, whId, "DIRECTOR").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("regenerateInvitationCode: WORKER → 403")
    void regenerateInvitationCode_givenWorkerRole_whenCalled_thenForbidden() {
        assertThat(controller.regenerateInvitationCode(UUID.randomUUID(), UUID.randomUUID(), "WORKER")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getActiveInvitationCodes: DIRECTOR → 200 OK")
    void getActiveInvitationCodes_givenDirectorRole_whenCalled_thenReturns200() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getActiveInvitationCodes(orgId)).thenReturn(List.of());

        assertThat(controller.getActiveInvitationCodes(orgId, "DIRECTOR").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getActiveInvitationCodes: WORKER → 403")
    void getActiveInvitationCodes_givenWorkerRole_whenCalled_thenForbidden() {
        assertThat(controller.getActiveInvitationCodes(UUID.randomUUID(), "WORKER")
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("validateInvitationCode: 200 OK")
    void validateInvitationCode_givenCode_whenCalled_thenReturns200() {
        when(organizationService.validateInvitationCode("ABC-123")).thenReturn(Map.of("valid", true));

        ResponseEntity<Map<String, Object>> response = controller.validateInvitationCode("ABC-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("valid", true);
    }
}
