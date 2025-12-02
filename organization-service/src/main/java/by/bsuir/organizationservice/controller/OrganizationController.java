package by.bsuir.organizationservice.controller;

import by.bsuir.organizationservice.dto.request.CreateOrganizationRequest;
import by.bsuir.organizationservice.dto.request.UpdateOrganizationRequest;
import by.bsuir.organizationservice.dto.response.InvitationCodeResponse;
import by.bsuir.organizationservice.dto.response.OrganizationDumpResponse;
import by.bsuir.organizationservice.dto.response.OrganizationResponse;
import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import by.bsuir.organizationservice.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;




    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole) {


        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        OrganizationResponse response = organizationService.createOrganization(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }




    @GetMapping("/{orgId}")
    public ResponseEntity<OrganizationResponse> getOrganization(@PathVariable UUID orgId) {
        OrganizationResponse response = organizationService.getOrganization(orgId);
        return ResponseEntity.ok(response);
    }




    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> getAllOrganizations(
            @RequestParam(required = false) OrganizationStatus status) {

        List<OrganizationResponse> response = status != null
                ? organizationService.getOrganizationsByStatus(status)
                : organizationService.getAllOrganizations();

        return ResponseEntity.ok(response);
    }




    @PutMapping("/{orgId}")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @PathVariable UUID orgId,
            @Valid @RequestBody UpdateOrganizationRequest request,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        OrganizationResponse response = organizationService.updateOrganization(orgId, request);
        return ResponseEntity.ok(response);
    }




    @DeleteMapping("/{orgId}")
    public ResponseEntity<OrganizationDumpResponse> deleteOrganization(
            @PathVariable UUID orgId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        OrganizationDumpResponse response = organizationService.deleteOrganization(orgId, userId);
        return ResponseEntity.ok(response);
    }




    @PostMapping("/{orgId}/invitation-codes/generate")
    public ResponseEntity<List<InvitationCodeResponse>> generateInvitationCodes(
            @PathVariable UUID orgId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<InvitationCodeResponse> response = organizationService.generateInvitationCodes(orgId);
        return ResponseEntity.ok(response);
    }




    @PostMapping("/{orgId}/warehouses/{warehouseId}/invitation-code/regenerate")
    public ResponseEntity<InvitationCodeResponse> regenerateInvitationCode(
            @PathVariable UUID orgId,
            @PathVariable UUID warehouseId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        InvitationCodeResponse response = organizationService.regenerateInvitationCodeForWarehouse(orgId, warehouseId);
        return ResponseEntity.ok(response);
    }




    @GetMapping("/{orgId}/invitation-codes")
    public ResponseEntity<List<InvitationCodeResponse>> getActiveInvitationCodes(
            @PathVariable UUID orgId,
            @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<InvitationCodeResponse> response = organizationService.getActiveInvitationCodes(orgId);
        return ResponseEntity.ok(response);
    }




    @GetMapping("/invitation-codes/{code}/validate")
    public ResponseEntity<Map<String, Object>> validateInvitationCode(@PathVariable String code) {
        Map<String, Object> response = organizationService.validateInvitationCode(code);
        return ResponseEntity.ok(response);
    }
}
