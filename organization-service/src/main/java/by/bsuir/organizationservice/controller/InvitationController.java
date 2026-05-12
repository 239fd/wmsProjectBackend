package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.CreateInvitationRequest;
import by.bsuir.organizationservice.dto.InvitationResponse;
import by.bsuir.organizationservice.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/organizations/{orgId}/invitations")
@RequiredArgsConstructor
@Tag(name = "Приглашения", description = "API для управления приглашениями сотрудников")
public class InvitationController {

    private final InvitationService invitationService;

    @Operation(
            summary = "Создать приглашение",
            description = "Создаёт приглашение для нового сотрудника и отправляет email. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Приглашение создано"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Организация не найдена"),
            @ApiResponse(responseCode = "409", description = "Активное приглашение уже существует")
    })
    @PostMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<InvitationResponse> createInvitation(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Valid @RequestBody CreateInvitationRequest request,
            Authentication authentication) {

        UUID createdBy = UUID.fromString(authentication.getName());
        InvitationResponse response = invitationService.createInvitation(orgId, request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Получить список приглашений организации",
            description = "Возвращает все приглашения организации. Доступно для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список приглашений получен"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @GetMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<List<InvitationResponse>> getOrganizationInvitations(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            Authentication authentication) {

        List<InvitationResponse> response = invitationService.getOrganizationInvitations(orgId);
        return ResponseEntity.ok(response);
    }
}