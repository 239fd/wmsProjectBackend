package by.bsuir.warehouseservice.service;

import by.bsuir.warehouseservice.config.RabbitMQConfig;
import by.bsuir.warehouseservice.dto.request.CreateWarehouseRequest;
import by.bsuir.warehouseservice.dto.request.UpdateWarehouseRequest;
import by.bsuir.warehouseservice.dto.response.WarehouseResponse;
import by.bsuir.warehouseservice.exception.AppException;
import by.bsuir.warehouseservice.model.entity.WarehouseEvent;
import by.bsuir.warehouseservice.model.entity.WarehouseReadModel;
import by.bsuir.warehouseservice.repository.WarehouseEventRepository;
import by.bsuir.warehouseservice.repository.WarehouseReadModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseReadModelRepository readModelRepository;
    private final WarehouseEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest request) {
        log.info("Creating warehouse: {} for organization: {}", request.name(), request.orgId());


        if (readModelRepository.existsByOrgIdAndName(request.orgId(), request.name())) {
            throw AppException.conflict("Склад с таким названием уже существует в данной организации");
        }

        UUID warehouseId = UUID.randomUUID();


        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orgId", request.orgId().toString());
        eventData.put("name", request.name());
        eventData.put("address", request.address());
        eventData.put("responsibleUserId", request.responsibleUserId() != null ? request.responsibleUserId().toString() : null);

        WarehouseEvent warehouseEvent = WarehouseEvent.builder()
                .warehouseId(warehouseId)
                .eventType("WAREHOUSE_CREATED")
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(warehouseEvent);


        WarehouseReadModel readModel = WarehouseReadModel.builder()
                .warehouseId(warehouseId)
                .orgId(request.orgId())
                .name(request.name())
                .address(request.address())
                .responsibleUserId(request.responsibleUserId())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        readModelRepository.save(readModel);


        publishWarehouseCreated(readModel);

        log.info("Warehouse created successfully with ID: {}", warehouseId);
        return mapToResponse(readModel);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouse(UUID warehouseId) {
        WarehouseReadModel warehouse = readModelRepository.findByWarehouseId(warehouseId)
                .orElseThrow(() -> AppException.notFound("Склад не найден"));
        return mapToResponse(warehouse);
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> getWarehousesByOrganization(UUID orgId) {
        return readModelRepository.findByOrgId(orgId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> getActiveWarehousesByOrganization(UUID orgId) {
        return readModelRepository.findByOrgIdAndIsActiveTrue(orgId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> getAllWarehouses() {
        return readModelRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public WarehouseResponse updateWarehouse(UUID warehouseId, UpdateWarehouseRequest request) {
        log.info("Updating warehouse: {}", warehouseId);

        WarehouseReadModel warehouse = readModelRepository.findByWarehouseId(warehouseId)
                .orElseThrow(() -> AppException.notFound("Склад не найден"));


        if (request.name() != null && !request.name().equals(warehouse.getName())) {
            if (readModelRepository.existsByOrgIdAndName(warehouse.getOrgId(), request.name())) {
                throw AppException.conflict("Склад с таким названием уже существует в данной организации");
            }
        }


        Map<String, Object> eventData = new HashMap<>();
        if (request.name() != null) eventData.put("name", request.name());
        if (request.address() != null) eventData.put("address", request.address());
        if (request.responsibleUserId() != null) eventData.put("responsibleUserId", request.responsibleUserId().toString());
        if (request.isActive() != null) eventData.put("isActive", request.isActive());

        WarehouseEvent warehouseEvent = WarehouseEvent.builder()
                .warehouseId(warehouseId)
                .eventType("WAREHOUSE_UPDATED")
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(warehouseEvent);


        if (request.name() != null) warehouse.setName(request.name());
        if (request.address() != null) warehouse.setAddress(request.address());
        if (request.responsibleUserId() != null) warehouse.setResponsibleUserId(request.responsibleUserId());
        if (request.isActive() != null) warehouse.setIsActive(request.isActive());
        warehouse.setUpdatedAt(LocalDateTime.now());

        readModelRepository.save(warehouse);


        publishWarehouseUpdated(warehouse);

        log.info("Warehouse updated successfully: {}", warehouseId);
        return mapToResponse(warehouse);
    }

    @Transactional
    public WarehouseResponse activateWarehouse(UUID warehouseId) {
        log.info("Activating warehouse: {}", warehouseId);

        WarehouseReadModel warehouse = readModelRepository.findByWarehouseId(warehouseId)
                .orElseThrow(() -> AppException.notFound("Склад не найден"));

        if (warehouse.getIsActive()) {
            throw AppException.badRequest("Склад уже активен");
        }


        Map<String, Object> eventData = new HashMap<>();
        eventData.put("isActive", true);

        WarehouseEvent warehouseEvent = WarehouseEvent.builder()
                .warehouseId(warehouseId)
                .eventType("WAREHOUSE_ACTIVATED")
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(warehouseEvent);


        warehouse.setIsActive(true);
        warehouse.setUpdatedAt(LocalDateTime.now());
        readModelRepository.save(warehouse);


        publishWarehouseUpdated(warehouse);

        log.info("Warehouse activated successfully: {}", warehouseId);
        return mapToResponse(warehouse);
    }

    @Transactional
    public WarehouseResponse deactivateWarehouse(UUID warehouseId) {
        log.info("Deactivating warehouse: {}", warehouseId);

        WarehouseReadModel warehouse = readModelRepository.findByWarehouseId(warehouseId)
                .orElseThrow(() -> AppException.notFound("Склад не найден"));

        if (!warehouse.getIsActive()) {
            throw AppException.badRequest("Склад уже деактивирован");
        }


        Map<String, Object> eventData = new HashMap<>();
        eventData.put("isActive", false);

        WarehouseEvent warehouseEvent = WarehouseEvent.builder()
                .warehouseId(warehouseId)
                .eventType("WAREHOUSE_DEACTIVATED")
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(warehouseEvent);


        warehouse.setIsActive(false);
        warehouse.setUpdatedAt(LocalDateTime.now());
        readModelRepository.save(warehouse);


        publishWarehouseUpdated(warehouse);

        log.info("Warehouse deactivated successfully: {}", warehouseId);
        return mapToResponse(warehouse);
    }

    @Transactional
    public void deleteWarehouse(UUID warehouseId) {
        log.info("Deleting warehouse: {}", warehouseId);

        WarehouseReadModel warehouse = readModelRepository.findByWarehouseId(warehouseId)
                .orElseThrow(() -> AppException.notFound("Склад не найден"));


        Map<String, Object> eventData = new HashMap<>();
        eventData.put("warehouseId", warehouseId.toString());
        eventData.put("orgId", warehouse.getOrgId().toString());
        eventData.put("name", warehouse.getName());

        WarehouseEvent warehouseEvent = WarehouseEvent.builder()
                .warehouseId(warehouseId)
                .eventType("WAREHOUSE_DELETED")
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(warehouseEvent);


        publishWarehouseDeleted(warehouse);


        readModelRepository.delete(warehouse);

        log.info("Warehouse deleted successfully: {}", warehouseId);
    }

    @Transactional
    public void deleteWarehousesByOrganization(UUID orgId) {
        log.info("Deleting all warehouses for organization: {}", orgId);

        List<WarehouseReadModel> warehouses = readModelRepository.findByOrgId(orgId);

        for (WarehouseReadModel warehouse : warehouses) {

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("warehouseId", warehouse.getWarehouseId().toString());
            eventData.put("orgId", orgId.toString());
            eventData.put("name", warehouse.getName());
            eventData.put("reason", "ORGANIZATION_DELETED");

            WarehouseEvent warehouseEvent = WarehouseEvent.builder()
                    .warehouseId(warehouse.getWarehouseId())
                    .eventType("WAREHOUSE_DELETED")
                    .eventData(objectMapper.valueToTree(eventData))
                    .eventVersion(1)
                    .createdAt(LocalDateTime.now())
                    .build();
            eventRepository.save(warehouseEvent);

            publishWarehouseDeleted(warehouse);
        }

        readModelRepository.deleteAll(warehouses);
        log.info("Deleted {} warehouses for organization: {}", warehouses.size(), orgId);
    }

    public Map<String, Object> getWarehouseInfo(UUID warehouseId) {
        WarehouseReadModel warehouse = readModelRepository.findByWarehouseId(warehouseId)
                .orElseThrow(() -> AppException.notFound("Склад не найден"));

        Map<String, Object> info = new HashMap<>();
        info.put("warehouseId", warehouse.getWarehouseId().toString());
        info.put("orgId", warehouse.getOrgId().toString());
        info.put("name", warehouse.getName());
        info.put("address", warehouse.getAddress());
        info.put("responsibleUserId", warehouse.getResponsibleUserId() != null ? warehouse.getResponsibleUserId().toString() : null);
        info.put("isActive", warehouse.getIsActive());
        info.put("createdAt", warehouse.getCreatedAt().toString());

        return info;
    }

    public List<Map<String, Object>> getWarehousesInfoByOrganization(UUID orgId) {
        List<WarehouseReadModel> warehouses = readModelRepository.findByOrgIdAndIsActiveTrue(orgId);

        return warehouses.stream()
                .map(warehouse -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("warehouseId", warehouse.getWarehouseId().toString());
                    info.put("name", warehouse.getName());
                    info.put("address", warehouse.getAddress());
                    info.put("isActive", warehouse.getIsActive());
                    return info;
                })
                .collect(Collectors.toList());
    }

    private WarehouseResponse mapToResponse(WarehouseReadModel model) {
        return new WarehouseResponse(
                model.getWarehouseId(),
                model.getOrgId(),
                model.getName(),
                model.getAddress(),
                model.getResponsibleUserId(),
                model.getIsActive(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }

    private void publishWarehouseCreated(WarehouseReadModel warehouse) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("warehouseId", warehouse.getWarehouseId().toString());
            message.put("orgId", warehouse.getOrgId().toString());
            message.put("name", warehouse.getName());
            message.put("eventType", "WAREHOUSE_CREATED");
            message.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.WAREHOUSE_EXCHANGE,
                    RabbitMQConfig.WAREHOUSE_CREATED_KEY,
                    message
            );
            log.info("Published warehouse.created event for: {}", warehouse.getWarehouseId());
        } catch (Exception e) {
            log.error("Failed to publish warehouse.created event for: {}. Error: {}",
                    warehouse.getWarehouseId(), e.getMessage());

        }
    }

    private void publishWarehouseUpdated(WarehouseReadModel warehouse) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("warehouseId", warehouse.getWarehouseId().toString());
            message.put("orgId", warehouse.getOrgId().toString());
            message.put("name", warehouse.getName());
            message.put("eventType", "WAREHOUSE_UPDATED");
            message.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.WAREHOUSE_EXCHANGE,
                    RabbitMQConfig.WAREHOUSE_UPDATED_KEY,
                    message
            );
            log.info("Published warehouse.updated event for: {}", warehouse.getWarehouseId());
        } catch (Exception e) {
            log.error("Failed to publish warehouse.updated event for: {}. Error: {}",
                    warehouse.getWarehouseId(), e.getMessage());
        }
    }

    private void publishWarehouseDeleted(WarehouseReadModel warehouse) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("warehouseId", warehouse.getWarehouseId().toString());
            message.put("orgId", warehouse.getOrgId().toString());
            message.put("name", warehouse.getName());
            message.put("eventType", "WAREHOUSE_DELETED");
            message.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.WAREHOUSE_EXCHANGE,
                    RabbitMQConfig.WAREHOUSE_DELETED_KEY,
                    message
            );
            log.info("Published warehouse.deleted event for: {}", warehouse.getWarehouseId());
        } catch (Exception e) {
            log.error("Failed to publish warehouse.deleted event for: {}. Error: {}",
                    warehouse.getWarehouseId(), e.getMessage());
        }
    }
}
