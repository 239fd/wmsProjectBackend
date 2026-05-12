package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.ValidateInvitationResponse;
import by.bsuir.organizationservice.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
@Tag(name = "Валидация приглашений", description = "Публичный API для валидации приглашений")
public class PublicInvitationController {

    private final InvitationService invitationService;

    @Operation(
            summary = "Валидировать приглашение",
            description = "Проверяет действительность токена приглашения. Доступно без авторизации"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Результат валидации получен")
    })
    @GetMapping("/validate")
    public ResponseEntity<ValidateInvitationResponse> validateInvitation(
            @Parameter(description = "Токен приглашения", required = true) @RequestParam UUID token) {

        ValidateInvitationResponse response = invitationService.validateInvitation(token);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Пометить приглашение как использованное",
            description = "Помечает приглашение как использованное после регистрации. Доступно без авторизации"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Приглашение помечено как использованное")
    })
    @PostMapping("/{token}/mark-used")
    public ResponseEntity<Map<String, String>> markAsUsed(
            @Parameter(description = "Токен приглашения", required = true) @PathVariable UUID token,
            @RequestBody Map<String, String> request) {

        UUID userId = UUID.fromString(request.get("userId"));
        invitationService.markAsUsed(token, userId);
        return ResponseEntity.ok(Map.of("message", "Приглашение помечено как использованное"));
    }
}