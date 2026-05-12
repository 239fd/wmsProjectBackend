package by.bsuir.ssoservice.controller;

import by.bsuir.ssoservice.exception.AppException;
import by.bsuir.ssoservice.model.entity.UserReadModel;
import by.bsuir.ssoservice.repository.UserReadModelRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
@Tag(name = "Внутренний API пользователей", description = "Inter-service API для org-service: чтение профиля и обновление organizationId/warehouseId")
public class InternalUserController {

    private final UserReadModelRepository userRepository;

    @Operation(summary = "Получить профиль пользователя (inter-service)")
    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable UUID userId) {
        UserReadModel user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("Пользователь не найден"));

        Map<String, Object> response = new HashMap<>();
        response.put("userId", user.getUserId().toString());
        response.put("email", user.getEmail());
        response.put("fullName", user.getFullName());
        response.put("username", user.getFullName());
        response.put("role", user.getRole().name());
        if (user.getOrganizationId() != null) {
            response.put("organizationId", user.getOrganizationId().toString());
        }
        if (user.getWarehouseId() != null) {
            response.put("warehouseId", user.getWarehouseId().toString());
        }
        response.put("isActive", user.getIsActive());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Пакетный lookup профилей по списку userId (inter-service)")
    @PostMapping("/lookup")
    public ResponseEntity<Map<String, Map<String, Object>>> lookupUsers(@RequestBody Map<String, List<String>> request) {
        List<String> rawIds = request.getOrDefault("ids", new ArrayList<>());
        if (rawIds.isEmpty()) {
            return ResponseEntity.ok(new HashMap<>());
        }

        List<UUID> ids = rawIds.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(UUID::fromString)
                .collect(Collectors.toList());

        List<UserReadModel> users = userRepository.findAllById(ids);

        Map<String, Map<String, Object>> result = new HashMap<>();
        for (UserReadModel user : users) {
            Map<String, Object> info = new HashMap<>();
            info.put("userId", user.getUserId().toString());
            info.put("email", user.getEmail());
            info.put("fullName", user.getFullName());
            info.put("username", user.getFullName());
            info.put("role", user.getRole().name());
            if (user.getOrganizationId() != null) {
                info.put("organizationId", user.getOrganizationId().toString());
            }
            if (user.getWarehouseId() != null) {
                info.put("warehouseId", user.getWarehouseId().toString());
            }
            info.put("isActive", user.getIsActive());
            result.put(user.getUserId().toString(), info);
        }
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Обновить organizationId/warehouseId пользователя (inter-service)")
    @PatchMapping("/{userId}/organization")
    @Transactional
    public ResponseEntity<Map<String, String>> updateOrganization(
            @PathVariable UUID userId,
            @RequestBody Map<String, String> request) {

        UserReadModel user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("Пользователь не найден"));

        String orgIdStr = request.get("organizationId");
        String warehouseIdStr = request.get("warehouseId");

        user.setOrganizationId(orgIdStr == null || orgIdStr.isBlank() ? null : UUID.fromString(orgIdStr));
        user.setWarehouseId(warehouseIdStr == null || warehouseIdStr.isBlank() ? null : UUID.fromString(warehouseIdStr));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Internal API: updated user {} organizationId={}, warehouseId={}",
                userId, orgIdStr, warehouseIdStr);

        return ResponseEntity.ok(Map.of("message", "Привязка пользователя обновлена"));
    }
}
