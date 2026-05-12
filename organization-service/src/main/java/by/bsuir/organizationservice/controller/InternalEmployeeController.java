package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.AddEmployeeRequest;
import by.bsuir.organizationservice.dto.EmployeeResponse;
import by.bsuir.organizationservice.service.EmployeeManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/internal/organizations")
@RequiredArgsConstructor
@Tag(name = "Внутренний API сотрудников", description = "Inter-service API для добавления сотрудников (вызывается SSO при регистрации по приглашению)")
public class InternalEmployeeController {

    private final EmployeeManagementService employeeManagementService;

    @Operation(
            summary = "Добавить сотрудника (inter-service)",
            description = "Создаёт запись в organization_employees. Вызывается SSO после успешной регистрации по приглашению. Без авторизации — endpoint предназначен только для inter-service вызовов через Eureka."
    )
    @PostMapping("/{orgId}/employees")
    public ResponseEntity<EmployeeResponse> addEmployee(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Valid @RequestBody AddEmployeeRequest request) {

        log.info("Internal API: adding employee {} (role={}) to organization {}",
                request.userId(), request.role(), orgId);

        EmployeeResponse response = employeeManagementService.addEmployee(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
