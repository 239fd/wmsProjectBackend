package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.AddEmployeeRequest;
import by.bsuir.organizationservice.dto.EmployeeResponse;
import by.bsuir.organizationservice.dto.response.OrganizationResponse;
import by.bsuir.organizationservice.exception.AppException;
import by.bsuir.organizationservice.model.entity.OrganizationEmployee;
import by.bsuir.organizationservice.repository.OrganizationEmployeeRepository;
import by.bsuir.organizationservice.service.EmployeeManagementService;
import by.bsuir.organizationservice.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/internal/organizations")
@RequiredArgsConstructor
@Tag(name = "Внутренний API организаций/сотрудников")
public class InternalEmployeeController {

    private final EmployeeManagementService employeeManagementService;
    private final OrganizationService organizationService;
    private final OrganizationEmployeeRepository employeeRepository;

    @Operation(summary = "Добавить сотрудника (inter-service)")
    @PostMapping("/{orgId}/employees")
    public ResponseEntity<EmployeeResponse> addEmployee(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Valid @RequestBody AddEmployeeRequest request) {

        log.info("Internal API: adding employee {} (role={}) to organization {}",
                request.userId(), request.role(), orgId);

        EmployeeResponse response = employeeManagementService.addEmployee(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Получить организацию по ID (inter-service)")
    @GetMapping("/{orgId}")
    public ResponseEntity<OrganizationResponse> getOrganization(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId) {

        log.debug("Internal API: fetching organization {}", orgId);
        OrganizationResponse response = organizationService.getOrganization(orgId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Получить активного заведующего организации (inter-service)")
    @GetMapping("/{orgId}/director")
    public ResponseEntity<Map<String, String>> getDirector(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId) {

        OrganizationEmployee director = employeeRepository
                .findFirstByOrgIdAndRoleAndIsActiveTrue(orgId, "DIRECTOR")
                .orElseThrow(() -> AppException.notFound("Активный заведующий организации не найден"));

        Map<String, String> response = new HashMap<>();
        response.put("userId", director.getUserId().toString());
        response.put("orgId", director.getOrgId().toString());
        response.put("role", director.getRole());
        return ResponseEntity.ok(response);
    }
}
