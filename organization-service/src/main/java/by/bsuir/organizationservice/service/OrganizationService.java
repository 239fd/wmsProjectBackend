package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.config.RabbitMQConfig;
import by.bsuir.organizationservice.dto.request.CreateOrganizationRequest;
import by.bsuir.organizationservice.dto.request.UpdateOrganizationRequest;
import by.bsuir.organizationservice.dto.response.InvitationCodeResponse;
import by.bsuir.organizationservice.dto.response.OrganizationDumpResponse;
import by.bsuir.organizationservice.dto.response.OrganizationResponse;
import by.bsuir.organizationservice.exception.AppException;
import by.bsuir.organizationservice.model.entity.OrganizationEvent;
import by.bsuir.organizationservice.model.entity.OrganizationInvitationCode;
import by.bsuir.organizationservice.model.entity.OrganizationReadModel;
import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import by.bsuir.organizationservice.model.event.OrganizationEvents;
import by.bsuir.organizationservice.repository.OrganizationEventRepository;
import by.bsuir.organizationservice.repository.OrganizationInvitationCodeRepository;
import by.bsuir.organizationservice.repository.OrganizationReadModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationReadModelRepository readModelRepository;
    private final OrganizationEventRepository eventRepository;
    private final OrganizationInvitationCodeRepository invitationCodeRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final WarehouseClientService warehouseClientService;

    @Value("${organization.invitation-code.ttl-hours:24}")
    private int invitationCodeTtlHours;

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request, UUID directorUserId) {
        log.info("Creating organization: {}", request.name());

        if (readModelRepository.existsByUnp(request.unp())) {
            throw AppException.conflict("Организация с таким УНП уже существует");
        }

        if (readModelRepository.existsByName(request.name())) {
            throw AppException.conflict("Организация с таким наименованием уже существует");
        }

        UUID orgId = UUID.randomUUID();

        OrganizationEvents.OrganizationCreatedEvent event = new OrganizationEvents.OrganizationCreatedEvent(
                request.name(),
                request.shortName(),
                request.unp(),
                request.address(),
                OrganizationStatus.ACTIVE
        );

        OrganizationEvent organizationEvent = OrganizationEvent.builder()
                .orgId(orgId)
                .eventType("ORGANIZATION_CREATED")
                .eventData(objectMapper.valueToTree(event))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(organizationEvent);

        OrganizationReadModel readModel = OrganizationReadModel.builder()
                .orgId(orgId)
                .name(request.name())
                .shortName(request.shortName())
                .unp(request.unp())
                .address(request.address())
                .status(OrganizationStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        readModelRepository.save(readModel);

        publishOrganizationCreated(readModel);

        log.info("Organization created successfully with ID: {}", orgId);
        return mapToResponse(readModel);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(UUID orgId) {
        OrganizationReadModel organization = readModelRepository.findByOrgId(orgId)
                .orElseThrow(() -> AppException.notFound("Организация не найдена"));
        return mapToResponse(organization);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> getAllOrganizations() {
        return readModelRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> getOrganizationsByStatus(OrganizationStatus status) {
        return readModelRepository.findAllByStatus(status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrganizationResponse updateOrganization(UUID orgId, UpdateOrganizationRequest request) {
        log.info("Updating organization: {}", orgId);

        OrganizationReadModel organization = readModelRepository.findByOrgId(orgId)
                .orElseThrow(() -> AppException.notFound("Организация не найдена"));

        if (request.unp() != null && !request.unp().equals(organization.getUnp())) {
            if (readModelRepository.existsByUnp(request.unp())) {
                throw AppException.conflict("Организация с таким УНП уже существует");
            }
        }

        if (request.name() != null && !request.name().equals(organization.getName())) {
            if (readModelRepository.existsByName(request.name())) {
                throw AppException.conflict("Организация с таким наименованием уже существует");
            }
        }

        OrganizationEvents.OrganizationUpdatedEvent event = new OrganizationEvents.OrganizationUpdatedEvent(
                request.name(),
                request.shortName(),
                request.unp(),
                request.address()
        );

        OrganizationEvent organizationEvent = OrganizationEvent.builder()
                .orgId(orgId)
                .eventType("ORGANIZATION_UPDATED")
                .eventData(objectMapper.valueToTree(event))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(organizationEvent);

        if (request.name() != null) organization.setName(request.name());
        if (request.shortName() != null) organization.setShortName(request.shortName());
        if (request.unp() != null) organization.setUnp(request.unp());
        if (request.address() != null) organization.setAddress(request.address());
        organization.setUpdatedAt(LocalDateTime.now());

        readModelRepository.save(organization);

        publishOrganizationUpdated(organization);

        log.info("Organization updated successfully: {}", orgId);
        return mapToResponse(organization);
    }

    @Transactional
    public OrganizationDumpResponse deleteOrganization(UUID orgId, UUID deletedByUserId) {
        log.info("Deleting organization: {}", orgId);

        OrganizationReadModel organization = readModelRepository.findByOrgId(orgId)
                .orElseThrow(() -> AppException.notFound("Организация не найдена"));

        Map<String, Object> dumpData = new HashMap<>();
        dumpData.put("orgId", organization.getOrgId().toString());
        dumpData.put("name", organization.getName());
        dumpData.put("shortName", organization.getShortName());
        dumpData.put("unp", organization.getUnp());
        dumpData.put("address", organization.getAddress());
        dumpData.put("status", organization.getStatus().name());
        dumpData.put("createdAt", organization.getCreatedAt().toString());
        dumpData.put("deletedAt", LocalDateTime.now().toString());
        dumpData.put("deletedBy", deletedByUserId.toString());

        List<OrganizationEvent> events = eventRepository.findByOrgIdOrderByCreatedAtAsc(orgId);
        dumpData.put("events", events);

        OrganizationEvents.OrganizationDeletedEvent event = new OrganizationEvents.OrganizationDeletedEvent(
                organization.getName(),
                organization.getUnp(),
                deletedByUserId.toString()
        );

        OrganizationEvent organizationEvent = OrganizationEvent.builder()
                .orgId(orgId)
                .eventType("ORGANIZATION_DELETED")
                .eventData(objectMapper.valueToTree(event))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(organizationEvent);

        publishOrganizationDeleted(organization);

        invitationCodeRepository.deactivateAllByOrgId(orgId);
        readModelRepository.delete(organization);

        log.info("Organization deleted successfully: {}", orgId);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return new OrganizationDumpResponse(
                "Организация успешно удалена. Дамп данных сохранён",
                dumpData,
                timestamp
        );
    }

    @Transactional
    public List<InvitationCodeResponse> generateInvitationCodes(UUID orgId) {
        log.info("Generating invitation codes for organization: {}", orgId);

        OrganizationReadModel organization = readModelRepository.findByOrgId(orgId)
                .orElseThrow(() -> AppException.notFound("Организация не найдена"));

        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw AppException.badRequest("Невозможно сгенерировать коды для неактивной организации");
        }

        invitationCodeRepository.deactivateAllByOrgId(orgId);

        List<Map<String, Object>> warehouses = warehouseClientService.getWarehousesByOrganization(orgId);

        List<InvitationCodeResponse> codes = new ArrayList<>();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(invitationCodeTtlHours);

        for (Map<String, Object> warehouse : warehouses) {
            UUID warehouseId = UUID.fromString(warehouse.get("warehouseId").toString());
            String warehouseName = warehouse.get("name").toString();

            String invitationCode = generateUniqueCode();

            OrganizationInvitationCode code = OrganizationInvitationCode.builder()
                    .codeId(UUID.randomUUID())
                    .orgId(orgId)
                    .warehouseId(warehouseId)
                    .invitationCode(invitationCode)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(expiresAt)
                    .isActive(true)
                    .build();

            invitationCodeRepository.save(code);

            OrganizationEvents.InvitationCodeGeneratedEvent event =
                    new OrganizationEvents.InvitationCodeGeneratedEvent(
                            invitationCode,
                            warehouseId.toString(),
                            expiresAt.toString()
                    );

            OrganizationEvent organizationEvent = OrganizationEvent.builder()
                    .orgId(orgId)
                    .eventType("INVITATION_CODE_GENERATED")
                    .eventData(objectMapper.valueToTree(event))
                    .eventVersion(1)
                    .createdAt(LocalDateTime.now())
                    .build();
            eventRepository.save(organizationEvent);

            codes.add(new InvitationCodeResponse(invitationCode, warehouseId, warehouseName, expiresAt));
        }

        log.info("Generated {} invitation codes for organization {}", codes.size(), orgId);
        return codes;
    }

    @Transactional
    public InvitationCodeResponse regenerateInvitationCodeForWarehouse(UUID orgId, UUID warehouseId) {
        log.info("Regenerating invitation code for org {} warehouse {}", orgId, warehouseId);

        OrganizationReadModel organization = readModelRepository.findByOrgId(orgId)
                .orElseThrow(() -> AppException.notFound("Организация не найдена"));

        invitationCodeRepository.deactivateAllByOrgIdAndWarehouseId(orgId, warehouseId);

        Map<String, Object> warehouse = warehouseClientService.getWarehouseInfo(warehouseId);
        String warehouseName = warehouse.get("name").toString();

        String invitationCode = generateUniqueCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(invitationCodeTtlHours);

        OrganizationInvitationCode code = OrganizationInvitationCode.builder()
                .codeId(UUID.randomUUID())
                .orgId(orgId)
                .warehouseId(warehouseId)
                .invitationCode(invitationCode)
                .createdAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .isActive(true)
                .build();

        invitationCodeRepository.save(code);

        log.info("Regenerated invitation code for warehouse {}", warehouseId);
        return new InvitationCodeResponse(invitationCode, warehouseId, warehouseName, expiresAt);
    }

    @Transactional(readOnly = true)
    public List<InvitationCodeResponse> getActiveInvitationCodes(UUID orgId) {
        List<OrganizationInvitationCode> codes = invitationCodeRepository.findByOrgIdAndIsActiveTrue(orgId);

        return codes.stream()
                .filter(code -> !code.isExpired())
                .map(code -> {
                    String warehouseName = "";
                    if (code.getWarehouseId() != null) {
                        try {
                            Map<String, Object> warehouse = warehouseClientService.getWarehouseInfo(code.getWarehouseId());
                            warehouseName = warehouse.get("name").toString();
                        } catch (Exception e) {
                            log.warn("Failed to get warehouse name for {}", code.getWarehouseId());
                        }
                    }
                    return new InvitationCodeResponse(
                            code.getInvitationCode(),
                            code.getWarehouseId(),
                            warehouseName,
                            code.getExpiresAt()
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validateInvitationCode(String code) {
        OrganizationInvitationCode invitationCode = invitationCodeRepository.findByInvitationCode(code)
                .orElseThrow(() -> AppException.notFound("Инвайт-код не найден"));

        if (!invitationCode.getIsActive() || invitationCode.isExpired()) {
            throw AppException.badRequest("Инвайт-код недействителен или истёк");
        }

        OrganizationReadModel organization = readModelRepository.findByOrgId(invitationCode.getOrgId())
                .orElseThrow(() -> AppException.notFound("Организация не найдена"));

        Map<String, Object> result = new HashMap<>();
        result.put("orgId", organization.getOrgId());
        result.put("orgName", organization.getName());
        result.put("warehouseId", invitationCode.getWarehouseId());
        result.put("expiresAt", invitationCode.getExpiresAt());

        return result;
    }

    private String generateUniqueCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private OrganizationResponse mapToResponse(OrganizationReadModel model) {
        return new OrganizationResponse(
                model.getOrgId(),
                model.getName(),
                model.getShortName(),
                model.getUnp(),
                model.getAddress(),
                model.getStatus(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }

    private void publishOrganizationCreated(OrganizationReadModel organization) {
        Map<String, Object> message = new HashMap<>();
        message.put("orgId", organization.getOrgId().toString());
        message.put("name", organization.getName());
        message.put("unp", organization.getUnp());
        message.put("eventType", "ORGANIZATION_CREATED");
        message.put("timestamp", LocalDateTime.now().toString());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORGANIZATION_EXCHANGE,
                    RabbitMQConfig.ORGANIZATION_CREATED_KEY,
                    message
            );
            log.info("Published organization.created event for: {}", organization.getOrgId());
        } catch (Exception e) {
            log.error("Failed to publish organization.created event for: {}. Error: {}",
                    organization.getOrgId(), e.getMessage());
        }
    }

    private void publishOrganizationUpdated(OrganizationReadModel organization) {
        Map<String, Object> message = new HashMap<>();
        message.put("orgId", organization.getOrgId().toString());
        message.put("name", organization.getName());
        message.put("unp", organization.getUnp());
        message.put("eventType", "ORGANIZATION_UPDATED");
        message.put("timestamp", LocalDateTime.now().toString());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORGANIZATION_EXCHANGE,
                    RabbitMQConfig.ORGANIZATION_UPDATED_KEY,
                    message
            );
            log.info("Published organization.updated event for: {}", organization.getOrgId());
        } catch (Exception e) {
            log.error("Failed to publish organization.updated event for: {}. Error: {}",
                    organization.getOrgId(), e.getMessage());
        }
    }

    private void publishOrganizationDeleted(OrganizationReadModel organization) {
        Map<String, Object> message = new HashMap<>();
        message.put("orgId", organization.getOrgId().toString());
        message.put("name", organization.getName());
        message.put("eventType", "ORGANIZATION_DELETED");
        message.put("timestamp", LocalDateTime.now().toString());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ORGANIZATION_EXCHANGE,
                    RabbitMQConfig.ORGANIZATION_DELETED_KEY,
                    message
            );
            log.info("Published organization.deleted event for: {}", organization.getOrgId());
        } catch (Exception e) {
            log.error("Failed to publish organization.deleted event for: {}. Error: {}",
                    organization.getOrgId(), e.getMessage());
        }
    }
}
