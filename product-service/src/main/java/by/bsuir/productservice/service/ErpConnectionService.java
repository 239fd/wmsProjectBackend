package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.ErpConnectionRequest;
import by.bsuir.productservice.dto.response.ErpConnectionResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.ErpConnection;
import by.bsuir.productservice.repository.ErpConnectionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErpConnectionService {

    private final ErpConnectionRepository repository;

    @Transactional
    public ErpConnectionResponse create(ErpConnectionRequest request, UUID organizationId, UUID userId) {
        if (organizationId == null) {
            throw AppException.badRequest("X-Organization-Id обязателен");
        }
        ErpConnection entity = ErpConnection.builder()
                .organizationId(organizationId)
                .aggregator(request.aggregator())
                .name(request.name())
                .username(request.username())
                .password(request.password())
                .basePath(request.basePath())
                .sectionName(request.sectionName())
                .journalName(request.journalName())
                .driverUrl(request.driverUrl())
                .isDefault(Boolean.TRUE.equals(request.isDefault()))
                .createdBy(userId)
                .build();
        if (entity.getIsDefault()) {
            unsetExistingDefaults(organizationId, null);
        }
        repository.save(entity);
        log.info("ERP connection created: {} (org={}, aggregator={})",
                entity.getConnectionId(), organizationId, entity.getAggregator());
        return mapToResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ErpConnectionResponse> list(UUID organizationId) {
        if (organizationId == null) {
            throw AppException.badRequest("X-Organization-Id обязателен");
        }
        return repository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ErpConnectionResponse get(UUID connectionId, UUID organizationId) {
        return mapToResponse(findOwned(connectionId, organizationId));
    }

    @Transactional
    public ErpConnectionResponse update(UUID connectionId, ErpConnectionRequest request, UUID organizationId) {
        ErpConnection entity = findOwned(connectionId, organizationId);
        entity.setAggregator(request.aggregator());
        entity.setName(request.name());
        entity.setUsername(request.username());
        if (request.password() != null && !request.password().isEmpty()) {
            entity.setPassword(request.password());
        }
        entity.setBasePath(request.basePath());
        entity.setSectionName(request.sectionName());
        entity.setJournalName(request.journalName());
        entity.setDriverUrl(request.driverUrl());
        if (Boolean.TRUE.equals(request.isDefault()) && !Boolean.TRUE.equals(entity.getIsDefault())) {
            unsetExistingDefaults(organizationId, connectionId);
            entity.setIsDefault(true);
        } else if (Boolean.FALSE.equals(request.isDefault())) {
            entity.setIsDefault(false);
        }
        repository.save(entity);
        return mapToResponse(entity);
    }

    @Transactional
    public void delete(UUID connectionId, UUID organizationId) {
        ErpConnection entity = findOwned(connectionId, organizationId);
        repository.delete(entity);
    }

    @Transactional
    public ErpConnectionResponse setDefault(UUID connectionId, UUID organizationId) {
        ErpConnection entity = findOwned(connectionId, organizationId);
        unsetExistingDefaults(organizationId, connectionId);
        entity.setIsDefault(true);
        repository.save(entity);
        return mapToResponse(entity);
    }

    @Transactional(readOnly = true)
    public Optional<ErpConnection> findDefault(UUID organizationId, String aggregator) {
        if (organizationId == null) {
            return Optional.empty();
        }
        if (aggregator != null && !aggregator.isBlank()) {
            return repository.findFirstByOrganizationIdAndAggregatorAndIsDefaultTrue(organizationId, aggregator);
        }
        return repository.findFirstByOrganizationIdAndIsDefaultTrue(organizationId);
    }

    @Transactional(readOnly = true)
    public Optional<ErpConnection> findById(UUID connectionId, UUID organizationId) {
        if (connectionId == null || organizationId == null) {
            return Optional.empty();
        }
        return repository.findByConnectionIdAndOrganizationId(connectionId, organizationId);
    }

    private ErpConnection findOwned(UUID connectionId, UUID organizationId) {
        if (organizationId == null) {
            throw AppException.badRequest("X-Organization-Id обязателен");
        }
        return repository.findByConnectionIdAndOrganizationId(connectionId, organizationId)
                .orElseThrow(() -> AppException.notFound("Подключение не найдено: " + connectionId));
    }

    private void unsetExistingDefaults(UUID organizationId, UUID exceptId) {
        List<ErpConnection> existing = repository.findByOrganizationIdAndIsDefaultTrue(organizationId);
        for (ErpConnection e : existing) {
            if (exceptId == null || !e.getConnectionId().equals(exceptId)) {
                e.setIsDefault(false);
                repository.save(e);
            }
        }
    }

    private ErpConnectionResponse mapToResponse(ErpConnection e) {
        return new ErpConnectionResponse(
                e.getConnectionId(),
                e.getOrganizationId(),
                e.getAggregator(),
                e.getName(),
                e.getUsername(),
                e.getPassword() != null && !e.getPassword().isEmpty(),
                e.getBasePath(),
                e.getSectionName(),
                e.getJournalName(),
                e.getDriverUrl(),
                Boolean.TRUE.equals(e.getIsDefault()),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
