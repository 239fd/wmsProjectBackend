package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.request.CreateOrganizationRequest;
import by.bsuir.organizationservice.dto.request.UpdateOrganizationRequest;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationController Unit Tests")
class OrganizationControllerTest {

    @Mock
    private OrganizationService organizationService;

    @InjectMocks
    private OrganizationController organizationController;

    @Test
    @DisplayName("createOrganization: Given DIRECTOR role Should create and return 201")
    void createOrganization_GivenDirectorRole_ShouldCreateAndReturn201() {
        UUID userId = UUID.randomUUID();
        CreateOrganizationRequest request = new CreateOrganizationRequest(
                "ООО Тест",
                "Тест",
                "123456789",
                "Адрес"
        );

        LocalDateTime now = LocalDateTime.now();
        OrganizationResponse expectedResponse = new OrganizationResponse(
                UUID.randomUUID(),
                "ООО Тест",
                "Тест",
                "123456789",
                "Адрес",
                OrganizationStatus.ACTIVE,
                now,
                now
        );

        when(organizationService.createOrganization(any(CreateOrganizationRequest.class), eq(userId)))
                .thenReturn(expectedResponse);

        ResponseEntity<OrganizationResponse> response = organizationController.createOrganization(
                request, userId, "DIRECTOR"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(organizationService).createOrganization(request, userId);
    }

    @Test
    @DisplayName("createOrganization: Given non-DIRECTOR role Should return 403")
    void createOrganization_GivenNonDirectorRole_ShouldReturn403() {
        UUID userId = UUID.randomUUID();
        CreateOrganizationRequest request = new CreateOrganizationRequest(
                "ООО Тест",
                "Тест",
                "123456789",
                "Адрес"
        );

        ResponseEntity<OrganizationResponse> response = organizationController.createOrganization(
                request, userId, "WORKER"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNull();
        verify(organizationService, never()).createOrganization(any(), any());
    }

    @Test
    @DisplayName("getOrganization: Given valid orgId Should return organization")
    void getOrganization_GivenValidOrgId_ShouldReturnOrganization() {
        UUID orgId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        OrganizationResponse expectedResponse = new OrganizationResponse(
                orgId,
                "ООО Тест",
                "Тест",
                "123456789",
                "Адрес",
                OrganizationStatus.ACTIVE,
                now,
                now
        );

        when(organizationService.getOrganization(orgId)).thenReturn(expectedResponse);

        ResponseEntity<OrganizationResponse> response = organizationController.getOrganization(orgId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(organizationService).getOrganization(orgId);
    }

    @Test
    @DisplayName("getAllOrganizations: Given no status Should return all organizations")
    void getAllOrganizations_GivenNoStatus_ShouldReturnAllOrganizations() {
        LocalDateTime now = LocalDateTime.now();
        List<OrganizationResponse> expectedList = List.of(
                new OrganizationResponse(UUID.randomUUID(), "Org 1", "O1", "111", "Addr1", OrganizationStatus.ACTIVE, now, now),
                new OrganizationResponse(UUID.randomUUID(), "Org 2", "O2", "222", "Addr2", OrganizationStatus.BLOCKED, now, now)
        );

        when(organizationService.getAllOrganizations()).thenReturn(expectedList);

        ResponseEntity<List<OrganizationResponse>> response = organizationController.getAllOrganizations(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        verify(organizationService).getAllOrganizations();
        verify(organizationService, never()).getOrganizationsByStatus(any());
    }

    @Test
    @DisplayName("getAllOrganizations: Given status Should return filtered organizations")
    void getAllOrganizations_GivenStatus_ShouldReturnFilteredOrganizations() {
        LocalDateTime now = LocalDateTime.now();
        List<OrganizationResponse> expectedList = List.of(
                new OrganizationResponse(UUID.randomUUID(), "Org 1", "O1", "111", "Addr1", OrganizationStatus.ACTIVE, now, now)
        );

        when(organizationService.getOrganizationsByStatus(OrganizationStatus.ACTIVE)).thenReturn(expectedList);

        ResponseEntity<List<OrganizationResponse>> response = organizationController.getAllOrganizations(OrganizationStatus.ACTIVE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(organizationService).getOrganizationsByStatus(OrganizationStatus.ACTIVE);
        verify(organizationService, never()).getAllOrganizations();
    }

    @Test
    @DisplayName("updateOrganization: Given valid request Should update and return organization")
    void updateOrganization_GivenValidRequest_ShouldUpdateAndReturnOrganization() {
        UUID orgId = UUID.randomUUID();
        UpdateOrganizationRequest request = new UpdateOrganizationRequest(
                "Новое название",
                null,
                null,
                null
        );

        OrganizationResponse expectedResponse = new OrganizationResponse(
                orgId,
                "Новое название",
                "Тест",
                "123456789",
                "Адрес",
                OrganizationStatus.ACTIVE,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(organizationService.updateOrganization(orgId, request)).thenReturn(expectedResponse);

        ResponseEntity<OrganizationResponse> response = organizationController.updateOrganization(
                orgId, request, "DIRECTOR"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(organizationService).updateOrganization(orgId, request);
    }

    @Test
    @DisplayName("deleteOrganization: Given valid orgId Should delete and return dump")
    void deleteOrganization_GivenValidOrgId_ShouldDeleteAndReturnDump() {
        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        OrganizationDumpResponse expectedResponse = new OrganizationDumpResponse(
                "Организация удалена",
                Map.of("orgId", orgId.toString()),
                "2025-12-03T14:00:00"
        );

        when(organizationService.deleteOrganization(orgId, userId)).thenReturn(expectedResponse);

        ResponseEntity<OrganizationDumpResponse> response = organizationController.deleteOrganization(
                orgId, userId, "DIRECTOR"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(organizationService).deleteOrganization(orgId, userId);
    }
}

