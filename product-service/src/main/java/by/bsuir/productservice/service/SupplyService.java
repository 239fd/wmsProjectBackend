package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateSupplyRequest;
import by.bsuir.productservice.dto.response.SupplyResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Supply;
import by.bsuir.productservice.model.entity.SupplyItem;
import by.bsuir.productservice.model.enums.SupplyStatus;
import by.bsuir.productservice.repository.SupplyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplyService {

    private final SupplyRepository supplyRepository;

    @Transactional(readOnly = true)
    public List<SupplyResponse> getAll() {
        return supplyRepository.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SupplyResponse> getAll(Pageable pageable) {
        return supplyRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SupplyResponse getById(UUID supplyId) {
        return toResponse(find(supplyId));
    }

    @Transactional(readOnly = true)
    public List<SupplyResponse> getByWarehouse(UUID warehouseId) {
        return supplyRepository.findByWarehouseId(warehouseId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SupplyResponse> getByWarehouse(UUID warehouseId, Pageable pageable) {
        return supplyRepository.findByWarehouseId(warehouseId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SupplyResponse> getByStatus(SupplyStatus status) {
        return supplyRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SupplyResponse> getByStatus(SupplyStatus status, Pageable pageable) {
        return supplyRepository.findByStatus(status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SupplyResponse> getByOrganization(UUID organizationId) {
        return supplyRepository.findByOrganizationId(organizationId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SupplyResponse> getByOrganization(UUID organizationId, Pageable pageable) {
        return supplyRepository.findByOrganizationId(organizationId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<SupplyResponse> getByOrganizationAndStatus(UUID organizationId, SupplyStatus status) {
        return supplyRepository.findByOrganizationIdAndStatus(organizationId, status).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<SupplyResponse> getByOrganizationAndStatus(UUID organizationId, SupplyStatus status, Pageable pageable) {
        return supplyRepository.findByOrganizationIdAndStatus(organizationId, status, pageable).map(this::toResponse);
    }

    @Transactional
    public SupplyResponse create(CreateSupplyRequest request) {
        return create(request, null);
    }

    @Transactional
    public SupplyResponse create(CreateSupplyRequest request, UUID organizationId) {
        log.info("Creating supply for warehouse: {}", request.warehouseId());

        UUID supplyId = UUID.randomUUID();

        List<SupplyItem> items = new ArrayList<>();
        if (request.items() != null) {
            for (CreateSupplyRequest.SupplyItemRequest itemReq : request.items()) {
                items.add(SupplyItem.builder()
                        .supplyId(supplyId)
                        .productId(itemReq.productId())
                        .expectedQty(itemReq.expectedQty())
                        .unitPrice(itemReq.unitPrice())
                        .notes(itemReq.notes())
                        .build());
            }
        }

        Supply supply = Supply.builder()
                .supplyId(supplyId)
                .organizationId(organizationId)
                .supplierId(request.supplierId())
                .warehouseId(request.warehouseId())
                .status(SupplyStatus.PLANNED)
                .expectedDate(request.expectedDate())
                .totalItems(items.size())
                .notes(request.notes())
                .createdBy(request.createdBy())
                .items(items)
                .build();

        supplyRepository.save(supply);
        log.info("Supply created: {}", supply.getSupplyId());
        return toResponse(supply);
    }

    @Transactional
    public SupplyResponse updateStatus(UUID supplyId, SupplyStatus newStatus, UUID userId) {
        log.info("Updating supply {} status to {}", supplyId, newStatus);
        Supply supply = find(supplyId);

        validateTransition(supply.getStatus(), newStatus);

        supply.setStatus(newStatus);
        if (newStatus == SupplyStatus.ACCEPTED || newStatus == SupplyStatus.REJECTED) {
            supply.setActualDate(LocalDate.now());
        }

        supplyRepository.save(supply);
        log.info("Supply {} status updated to {}", supplyId, newStatus);
        return toResponse(supply);
    }

    @Transactional
    public void delete(UUID supplyId) {
        Supply supply = find(supplyId);
        if (supply.getStatus() != SupplyStatus.PLANNED) {
            throw AppException.badRequest("Можно удалить только поставку в статусе PLANNED");
        }
        supply.setStatus(SupplyStatus.CANCELLED);
        supplyRepository.save(supply);
    }

    private void validateTransition(SupplyStatus current, SupplyStatus next) {
        boolean valid = switch (current) {
            case PLANNED -> next == SupplyStatus.IN_PROGRESS || next == SupplyStatus.CANCELLED;
            case IN_PROGRESS -> next == SupplyStatus.ACCEPTED || next == SupplyStatus.REJECTED;
            default -> false;
        };
        if (!valid) {
            throw AppException.badRequest(
                    String.format("Недопустимый переход статуса: %s → %s", current, next));
        }
    }

    private Supply find(UUID supplyId) {
        return supplyRepository.findById(supplyId)
                .orElseThrow(() -> AppException.notFound("Поставка не найдена"));
    }

    private SupplyResponse toResponse(Supply s) {
        List<SupplyResponse.SupplyItemResponse> itemResponses = s.getItems() == null
                ? List.of()
                : s.getItems().stream()
                        .map(i -> new SupplyResponse.SupplyItemResponse(
                                i.getItemId(),
                                i.getProductId(),
                                i.getExpectedQty(),
                                i.getActualQty(),
                                i.getUnitPrice(),
                                i.getNotes()))
                        .collect(Collectors.toList());

        return new SupplyResponse(
                s.getSupplyId(),
                s.getOrganizationId(),
                s.getSupplierId(),
                s.getWarehouseId(),
                s.getStatus(),
                s.getExpectedDate(),
                s.getActualDate(),
                s.getTotalItems(),
                s.getNotes(),
                s.getCreatedBy(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                itemResponses
        );
    }
}