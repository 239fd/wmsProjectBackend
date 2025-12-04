package by.bsuir.ssoservice.controller;

import by.bsuir.ssoservice.dto.request.UpdateProfileRequest;
import by.bsuir.ssoservice.dto.response.SessionInfo;
import by.bsuir.ssoservice.dto.response.UserResponse;
import by.bsuir.ssoservice.exception.AppException;
import by.bsuir.ssoservice.service.ProfileService;
import by.bsuir.ssoservice.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Tag(name = "Профиль пользователя", description = "API для управления профилем, фото и активными сессиями")
public class ProfileController {

    private final ProfileService profileService;

    @Operation(
            summary = "Получить профиль текущего пользователя",
            description = "Возвращает информацию о профиле аутентифицированного пользователя"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Профиль получен",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Неавторизован")
    })
    @GetMapping
    public ResponseEntity<UserResponse> getCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserResponse user = profileService.getUserProfile(userId);
        return ResponseEntity.ok(user);
    }

    @Operation(
            summary = "Обновить профиль",
            description = "Обновляет информацию профиля пользователя (имя, фамилия, телефон, дата рождения)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Профиль обновлен",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "401", description = "Неавторизован")
    })
    @PutMapping
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserResponse updatedUser = profileService.updateProfile(userId, request);
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(
            summary = "Загрузить фото профиля",
            description = "Загружает фото профиля пользователя (максимум 5 МБ, форматы: jpg, png, gif)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Фото загружено"),
            @ApiResponse(responseCode = "400", description = "Файл слишком большой или неверный формат"),
            @ApiResponse(responseCode = "401", description = "Неавторизован")
    })
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadPhoto(
            @Parameter(description = "Файл изображения", required = true) @RequestParam("photo") MultipartFile photo) throws IOException {
        UUID userId = SecurityUtils.getCurrentUserId();

        if (photo.getSize() > 5 * 1024 * 1024) {
            throw AppException.badRequest("Размер файла не должен превышать 5 МБ");
        }

        String contentType = photo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw AppException.badRequest("Файл должен быть изображением");
        }

        String photoUrl = profileService.uploadPhoto(userId, photo);
        return ResponseEntity.ok(Map.of("photoUrl", photoUrl));
    }

    @Operation(
            summary = "Удалить фото профиля",
            description = "Удаляет фото профиля пользователя"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Фото удалено"),
            @ApiResponse(responseCode = "401", description = "Неавторизован")
    })
    @DeleteMapping("/photo")
    public ResponseEntity<Map<String, String>> deletePhoto() {
        UUID userId = SecurityUtils.getCurrentUserId();
        profileService.deletePhoto(userId);
        return ResponseEntity.ok(Map.of("message", "Фото удалено"));
    }

    @Operation(
            summary = "Получить активные сессии",
            description = "Возвращает список всех активных сессий текущего пользователя"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список сессий получен"),
            @ApiResponse(responseCode = "401", description = "Неавторизован")
    })
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionInfo>> getActiveSessions(HttpServletRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String currentToken = request.getHeader("X-Refresh-Token");
        List<SessionInfo> sessions = profileService.getActiveSessions(userId, currentToken);
        return ResponseEntity.ok(sessions);
    }

    @Operation(
            summary = "Завершить сессию",
            description = "Завершает конкретную сессию пользователя по её идентификатору"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Сессия завершена"),
            @ApiResponse(responseCode = "401", description = "Неавторизован"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена")
    })
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, String>> terminateSession(
            @Parameter(description = "ID сессии", required = true) @PathVariable Integer sessionId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        profileService.terminateSession(userId, sessionId);
        return ResponseEntity.ok(Map.of("message", "Сессия завершена"));
    }

    @Operation(
            summary = "Завершить все сессии",
            description = "Завершает все активные сессии пользователя, кроме текущей"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Все сессии завершены"),
            @ApiResponse(responseCode = "401", description = "Неавторизован")
    })
    @DeleteMapping("/sessions")
    public ResponseEntity<Map<String, String>> terminateAllSessions(HttpServletRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String currentToken = request.getHeader("X-Refresh-Token");
        profileService.terminateAllSessions(userId, currentToken);
        return ResponseEntity.ok(Map.of("message", "Все сессии завершены"));
    }

    @Operation(
            summary = "Удалить аккаунт",
            description = "Полностью удаляет аккаунт текущего пользователя и все связанные данные"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Аккаунт удален"),
            @ApiResponse(responseCode = "401", description = "Неавторизован")
    })
    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteAccount() {
        UUID userId = SecurityUtils.getCurrentUserId();
        profileService.deleteAccount(userId);
        return ResponseEntity.ok(Map.of("message", "Аккаунт удален"));
    }
}
