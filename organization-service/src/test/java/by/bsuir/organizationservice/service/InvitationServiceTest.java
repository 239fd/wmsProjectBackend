package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.dto.CreateInvitationRequest;
import by.bsuir.organizationservice.dto.InvitationResponse;
import by.bsuir.organizationservice.dto.ValidateInvitationResponse;
import by.bsuir.organizationservice.exception.AppException;
import by.bsuir.organizationservice.model.entity.Invitation;
import by.bsuir.organizationservice.model.entity.OrganizationReadModel;
import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import by.bsuir.organizationservice.repository.InvitationRepository;
import by.bsuir.organizationservice.repository.OrganizationReadModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationService — модульные тесты")
class InvitationServiceTest {

    @Mock private InvitationRepository invitationRepository;
    @Mock private OrganizationReadModelRepository organizationRepository;
    @Mock private EmailService emailService;

    @InjectMocks private InvitationService service;

    private UUID orgId;
    private UUID createdBy;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        createdBy = UUID.randomUUID();
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:3000");
    }

    @Test
    @DisplayName("createInvitation: валидный запрос → сохраняет приглашение и шлёт email")
    void createInvitation_GivenValid_ShouldSaveAndSendEmail() {
        CreateInvitationRequest req = new CreateInvitationRequest(
                "worker@example.com", "WORKER", null);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(activeOrg()));
        when(invitationRepository.findByEmailAndUsedFalse("worker@example.com")).thenReturn(List.of());
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> {
            Invitation i = inv.getArgument(0);
            if (i.getInvitationId() == null) i.setInvitationId(UUID.randomUUID());
            if (i.getInvitationToken() == null) i.setInvitationToken(UUID.randomUUID());
            if (i.getCreatedAt() == null) i.setCreatedAt(LocalDateTime.now());
            if (i.getExpiresAt() == null) i.setExpiresAt(i.getCreatedAt().plusDays(7));
            if (i.getUsed() == null) i.setUsed(false);
            return i;
        });

        InvitationResponse response = service.createInvitation(orgId, req, createdBy);

        assertThat(response.email()).isEqualTo("worker@example.com");
        assertThat(response.role()).isEqualTo("WORKER");
        assertThat(response.used()).isFalse();
        verify(emailService).sendInvitation(
                eq("worker@example.com"), eq("ООО Ромашка"), eq("WORKER"), anyString());
    }

    @Test
    @DisplayName("createInvitation: неверная роль → 400 bad request")
    void createInvitation_GivenInvalidRole_ShouldThrowBadRequest() {
        CreateInvitationRequest req = new CreateInvitationRequest(
                "x@y.com", "ADMIN", null);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(activeOrg()));

        AppException ex = catchApp(() -> service.createInvitation(orgId, req, createdBy));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createInvitation: организация не найдена → 404")
    void createInvitation_GivenMissingOrg_ShouldThrowNotFound() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

        AppException ex = catchApp(() -> service.createInvitation(
                orgId, new CreateInvitationRequest("x@y.com", "WORKER", null), createdBy));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("createInvitation: активное приглашение для этого email уже есть → 409 conflict")
    void createInvitation_GivenActiveInvitationExists_ShouldThrowConflict() {
        CreateInvitationRequest req = new CreateInvitationRequest(
                "worker@example.com", "WORKER", null);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(activeOrg()));

        Invitation existing = Invitation.builder()
                .invitationId(UUID.randomUUID())
                .invitationToken(UUID.randomUUID())
                .orgId(orgId)
                .email("worker@example.com")
                .role("WORKER")
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .used(false)
                .build();
        when(invitationRepository.findByEmailAndUsedFalse("worker@example.com"))
                .thenReturn(List.of(existing));

        AppException ex = catchApp(() -> service.createInvitation(orgId, req, createdBy));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createInvitation: SMTP упал → приглашение всё равно сохраняется (graceful)")
    void createInvitation_WhenEmailFails_ShouldStillPersist() {
        CreateInvitationRequest req = new CreateInvitationRequest(
                "worker@example.com", "WORKER", null);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(activeOrg()));
        when(invitationRepository.findByEmailAndUsedFalse("worker@example.com")).thenReturn(List.of());
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(inv -> {
            Invitation i = inv.getArgument(0);
            if (i.getInvitationId() == null) i.setInvitationId(UUID.randomUUID());
            if (i.getInvitationToken() == null) i.setInvitationToken(UUID.randomUUID());
            if (i.getCreatedAt() == null) i.setCreatedAt(LocalDateTime.now());
            if (i.getExpiresAt() == null) i.setExpiresAt(i.getCreatedAt().plusDays(7));
            if (i.getUsed() == null) i.setUsed(false);
            return i;
        });
        doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendInvitation(anyString(), anyString(), anyString(), anyString());

        InvitationResponse response = service.createInvitation(orgId, req, createdBy);

        assertThat(response).isNotNull();
        verify(invitationRepository).save(any());
    }

    @Test
    @DisplayName("validateInvitation: токен не найден → invalid с понятным сообщением")
    void validateInvitation_GivenMissingToken_ShouldReturnInvalid() {
        UUID token = UUID.randomUUID();
        when(invitationRepository.findByInvitationToken(token)).thenReturn(Optional.empty());

        ValidateInvitationResponse response = service.validateInvitation(token);

        assertThat(response.valid()).isFalse();
        assertThat(response.message()).contains("не найдено");
    }

    @Test
    @DisplayName("validateInvitation: используется → invalid")
    void validateInvitation_GivenUsed_ShouldReturnInvalid() {
        UUID token = UUID.randomUUID();
        Invitation inv = sampleInvitation(token);
        inv.setUsed(true);
        when(invitationRepository.findByInvitationToken(token)).thenReturn(Optional.of(inv));

        ValidateInvitationResponse response = service.validateInvitation(token);

        assertThat(response.valid()).isFalse();
        assertThat(response.message()).contains("использовано");
    }

    @Test
    @DisplayName("validateInvitation: истёк срок → invalid")
    void validateInvitation_GivenExpired_ShouldReturnInvalid() {
        UUID token = UUID.randomUUID();
        Invitation inv = sampleInvitation(token);
        inv.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(invitationRepository.findByInvitationToken(token)).thenReturn(Optional.of(inv));

        ValidateInvitationResponse response = service.validateInvitation(token);

        assertThat(response.valid()).isFalse();
        assertThat(response.message()).contains("истёк");
    }

    @Test
    @DisplayName("validateInvitation: организация не найдена → invalid")
    void validateInvitation_GivenMissingOrg_ShouldReturnInvalid() {
        UUID token = UUID.randomUUID();
        Invitation inv = sampleInvitation(token);
        when(invitationRepository.findByInvitationToken(token)).thenReturn(Optional.of(inv));
        when(organizationRepository.findById(inv.getOrgId())).thenReturn(Optional.empty());

        ValidateInvitationResponse response = service.validateInvitation(token);

        assertThat(response.valid()).isFalse();
    }

    @Test
    @DisplayName("validateInvitation: всё валидно → возвращает данные орг + роль + email")
    void validateInvitation_GivenValid_ShouldReturnDetails() {
        UUID token = UUID.randomUUID();
        Invitation inv = sampleInvitation(token);
        when(invitationRepository.findByInvitationToken(token)).thenReturn(Optional.of(inv));
        when(organizationRepository.findById(inv.getOrgId())).thenReturn(Optional.of(activeOrg()));

        ValidateInvitationResponse response = service.validateInvitation(token);

        assertThat(response.valid()).isTrue();
        assertThat(response.organizationName()).isEqualTo("ООО Ромашка");
        assertThat(response.role()).isEqualTo("WORKER");
        assertThat(response.email()).isEqualTo("worker@example.com");
        assertThat(response.organizationId()).isEqualTo(inv.getOrgId());
    }

    @Test
    @DisplayName("markAsUsed: непротухшее приглашение → используется и сохраняется")
    void markAsUsed_GivenValid_ShouldMark() {
        UUID token = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Invitation inv = sampleInvitation(token);
        when(invitationRepository.findByInvitationTokenAndUsedFalse(token))
                .thenReturn(Optional.of(inv));

        service.markAsUsed(token, userId);

        ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).save(captor.capture());
        assertThat(captor.getValue().getUsed()).isTrue();
        assertThat(captor.getValue().getUsedAt()).isNotNull();
        assertThat(captor.getValue().getUsedBy()).isEqualTo(userId);
    }

    @Test
    @DisplayName("markAsUsed: токен не найден / уже использован → 404")
    void markAsUsed_GivenMissing_ShouldThrowNotFound() {
        UUID token = UUID.randomUUID();
        when(invitationRepository.findByInvitationTokenAndUsedFalse(token))
                .thenReturn(Optional.empty());

        AppException ex = catchApp(() -> service.markAsUsed(token, UUID.randomUUID()));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getOrganizationInvitations: маппит репозиторий в response-list")
    void getOrganizationInvitations_ShouldMapAll() {
        UUID token1 = UUID.randomUUID();
        UUID token2 = UUID.randomUUID();
        when(invitationRepository.findByOrgIdOrderByCreatedAtDesc(orgId))
                .thenReturn(List.of(sampleInvitation(token1), sampleInvitation(token2)));

        List<InvitationResponse> all = service.getOrganizationInvitations(orgId);

        assertThat(all).hasSize(2);
        assertThat(all.get(0).inviteLink()).contains("http://localhost:3000/register/invitation?token=");
    }

    private OrganizationReadModel activeOrg() {
        return OrganizationReadModel.builder()
                .orgId(orgId)
                .name("ООО Ромашка")
                .unp("123456789")
                .status(OrganizationStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Invitation sampleInvitation(UUID token) {
        return Invitation.builder()
                .invitationId(UUID.randomUUID())
                .invitationToken(token)
                .orgId(orgId)
                .email("worker@example.com")
                .role("WORKER")
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now().minusHours(1))
                .expiresAt(LocalDateTime.now().plusDays(6))
                .used(false)
                .build();
    }

    private static AppException catchApp(Runnable r) {
        try {
            r.run();
        } catch (AppException e) {
            return e;
        }
        throw new AssertionError("Expected AppException");
    }
}
