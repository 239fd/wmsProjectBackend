package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.service.EmployeeAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/organizations/{orgId}/analytics")
@RequiredArgsConstructor
@Tag(name = "Аналитика сотрудников", description = "API для получения аналитических данных о сотрудниках организации")
public class EmployeeAnalyticsController {

    private final EmployeeAnalyticsService analyticsService;

    @Operation(
            summary = "Получить аналитику по всем сотрудникам",
            description = "Возвращает аналитические данные по всем сотрудникам организации. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Аналитика успешно получена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Организация не найдена")
    })
    @GetMapping("/employees")
    public ResponseEntity<List<Map<String, Object>>> getEmployeesAnalytics(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }

        List<Map<String, Object>> analytics = analyticsService.getEmployeesAnalytics(orgId);
        return ResponseEntity.ok(analytics);
    }

    @Operation(
            summary = "Получить аналитику по конкретному сотруднику",
            description = "Возвращает детальную аналитику по конкретному сотруднику организации. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Аналитика успешно получена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Организация или сотрудник не найдены")
    })
    @GetMapping("/employees/{userId}")
    public ResponseEntity<Map<String, Object>> getEmployeeAnalytics(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Parameter(description = "ID пользователя (сотрудника)", required = true) @PathVariable UUID userId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> analytics = analyticsService.getEmployeeAnalytics(orgId, userId);
        return ResponseEntity.ok(analytics);
    }
}

