package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.dto.CreateInvitationRequest;
import by.bsuir.organizationservice.dto.InvitationResponse;
import by.bsuir.organizationservice.dto.ValidateInvitationResponse;
import by.bsuir.organizationservice.exception.AppException;
import by.bsuir.organizationservice.model.entity.Invitation;
import by.bsuir.organizationservice.model.entity.OrganizationReadModel;
import by.bsuir.organizationservice.repository.InvitationRepository;
import by.bsuir.organizationservice.repository.OrganizationReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final OrganizationReadModelRepository organizationRepository;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public InvitationResponse createInvitation(UUID orgId, CreateInvitationRequest request, UUID createdBy) {
        log.info("Creating invitation for email: {} to organization: {}", request.email(), orgId);

        OrganizationReadModel organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> AppException.notFound("Организация не найдена"));

        if (!isValidRole(request.role())) {
            throw AppException.badRequest("Недопустимая роль: " + request.role());
        }

        List<Invitation> existingInvitations = invitationRepository.findByEmailAndUsedFalse(request.email());
        for (Invitation existing : existingInvitations) {
            if (existing.getOrgId().equals(orgId) && existing.isValid()) {
                throw AppException.conflict("Активное приглашение для этого email уже существует");
            }
        }

        Invitation invitation = Invitation.builder()
                .orgId(orgId)
                .email(request.email())
                .role(request.role())
                .warehouseId(request.warehouseId())
                .createdBy(createdBy)
                .build();

        invitationRepository.save(invitation);

        try {
            emailService.sendInvitation(
                    request.email(),
                    organization.getName(),
                    request.role(),
                    invitation.getInvitationToken().toString()
            );
        } catch (Exception e) {
            log.error("Failed to send invitation email, but invitation created: {}", invitation.getInvitationId());
        }

        log.info("Invitation created successfully: {}", invitation.getInvitationId());
        return mapToResponse(invitation);
    }

    @Transactional(readOnly = true)
    public ValidateInvitationResponse validateInvitation(UUID invitationToken) {
        log.info("Validating invitation token: {}", invitationToken);

        Invitation invitation = invitationRepository.findByInvitationToken(invitationToken)
                .orElse(null);

        if (invitation == null) {
            return new ValidateInvitationResponse(false, null, null, null, "Приглашение не найдено");
        }

        if (invitation.getUsed()) {
            return new ValidateInvitationResponse(false, null, null, null, "Приглашение уже использовано");
        }

        if (invitation.isExpired()) {
            return new ValidateInvitationResponse(false, null, null, null, "Срок действия приглашения истёк");
        }

        OrganizationReadModel organization = organizationRepository.findById(invitation.getOrgId())
                .orElse(null);

        if (organization == null) {
            return new ValidateInvitationResponse(false, null, null, null, "Организация не найдена");
        }

        return new ValidateInvitationResponse(
                true,
                organization.getName(),
                invitation.getRole(),
                invitation.getEmail(),
                invitation.getOrgId(),
                invitation.getWarehouseId(),
                "Приглашение действительно"
        );
    }

    @Transactional
    public void markAsUsed(UUID invitationToken, UUID userId) {
        Invitation invitation = invitationRepository.findByInvitationTokenAndUsedFalse(invitationToken)
                .orElseThrow(() -> AppException.notFound("Приглашение не найдено или уже использовано"));

        invitation.setUsed(true);
        invitation.setUsedAt(LocalDateTime.now());
        invitation.setUsedBy(userId);
        invitationRepository.save(invitation);

        log.info("Invitation marked as used: {}", invitationToken);
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> getOrganizationInvitations(UUID orgId) {
        return invitationRepository.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cleanupExpiredInvitations() {
        invitationRepository.deleteExpiredInvitations(LocalDateTime.now());
        log.info("Expired invitations cleaned up");
    }

    private boolean isValidRole(String role) {
        return "WORKER".equals(role) || "ACCOUNTANT".equals(role) || "DIRECTOR".equals(role);
    }

    private InvitationResponse mapToResponse(Invitation invitation) {
        String inviteLink = frontendUrl + "/register?invite=" + invitation.getInvitationToken();
        return new InvitationResponse(
                invitation.getInvitationId(),
                invitation.getInvitationToken(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getWarehouseId(),
                inviteLink,
                invitation.getCreatedAt(),
                invitation.getExpiresAt(),
                invitation.getUsed()
        );
    }
}