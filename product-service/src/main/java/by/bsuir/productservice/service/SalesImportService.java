package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.import_.SalesOrderDto;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.entity.ShipmentRequest;
import by.bsuir.productservice.model.entity.ShipmentRequestItem;
import by.bsuir.productservice.model.enums.AllocationStrategy;
import by.bsuir.productservice.model.enums.DocumentLayout;
import by.bsuir.productservice.model.enums.DomesticDocumentKind;
import by.bsuir.productservice.model.enums.ShipmentRequestStatus;
import by.bsuir.productservice.model.enums.ShipmentType;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import by.bsuir.productservice.repository.ShipmentRequestItemRepository;
import by.bsuir.productservice.repository.ShipmentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesImportService {

    private final ShipmentRequestRepository requestRepository;
    private final ShipmentRequestItemRepository itemRepository;
    private final ProductReadModelRepository productRepository;

    @Transactional
    public ImportResult importSales(
            UUID organizationId,
            UUID warehouseId,
            UUID userId,
            List<SalesOrderDto> orders) {
        if (organizationId == null) {
            throw AppException.badRequest("organizationId обязателен для импорта отгрузок");
        }
        if (warehouseId == null) {
            throw AppException.badRequest("warehouseId обязателен — отгрузка привязана к складу");
        }
        if (orders == null || orders.isEmpty()) {
            return new ImportResult(0, 0, 0, List.of());
        }

        int imported = 0;
        int skipped = 0;
        int errored = 0;
        List<String> errors = new ArrayList<>();

        for (SalesOrderDto dto : orders) {
            try {
                if (dto.externalId() != null && !dto.externalId().isBlank()) {
                    Optional<ShipmentRequest> existing =
                            requestRepository.findFirstByOrganizationIdAndComment(
                                    organizationId, "external:" + dto.externalId());
                    if (existing.isPresent()) {
                        skipped++;
                        continue;
                    }
                }

                SalesOrderDto.CustomerDto customer = dto.customer();
                String customerName = customer != null ? customer.name() : null;
                String customerInn = customer != null ? customer.inn() : null;
                String customerAddress = customer != null ? customer.address() : null;

                String currency = dto.currency() != null && !dto.currency().isBlank()
                        ? dto.currency().toUpperCase().substring(0, Math.min(3, dto.currency().length()))
                        : "BYN";

                ShipmentRequest entity = ShipmentRequest.builder()
                        .requestId(UUID.randomUUID())
                        .organizationId(organizationId)
                        .warehouseId(warehouseId)
                        .recipientName(customerName)
                        .recipientAddress(customerAddress)
                        .recipientInn(customerInn)
                        .plannedDate(dto.expectedDate() != null ? dto.expectedDate() : dto.date())
                        .comment(dto.externalId() != null && !dto.externalId().isBlank()
                                ? "external:" + dto.externalId()
                                : null)
                        .status(ShipmentRequestStatus.PLANNED)
                        .strategy(AllocationStrategy.AUTO)
                        .shipmentType(ShipmentType.DOMESTIC)
                        .currency(currency)
                        .documentLayout(DocumentLayout.HORIZONTAL)
                        .domesticDocumentKind(DomesticDocumentKind.TN)
                        .createdBy(userId)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                requestRepository.save(entity);

                int created = 0;
                int missingProducts = 0;
                List<SalesOrderDto.SalesItemDto> items =
                        dto.shipmentItems() != null ? dto.shipmentItems() : List.of();
                for (SalesOrderDto.SalesItemDto itemDto : items) {
                    Optional<ProductReadModel> product = resolveProduct(organizationId, itemDto.name());
                    if (product.isEmpty()) {
                        missingProducts++;
                        continue;
                    }
                    BigDecimal qty = itemDto.qty() != null ? itemDto.qty() : BigDecimal.ZERO;
                    ShipmentRequestItem item = ShipmentRequestItem.builder()
                            .itemId(UUID.randomUUID())
                            .requestId(entity.getRequestId())
                            .productId(product.get().getProductId())
                            .expectedQty(qty)
                            .pickedQty(BigDecimal.ZERO)
                            .status("PENDING")
                            .build();
                    itemRepository.save(item);
                    created++;
                }
                if (missingProducts > 0) {
                    errors.add("Отгрузка " + dto.externalId() + ": не найдено товаров по названию: "
                            + missingProducts);
                }
                log.info("Sales order {} → ShipmentRequest {} (items: {} created, {} skipped)",
                        dto.externalId(), entity.getRequestId(), created, missingProducts);
                imported++;
            } catch (Exception ex) {
                errored++;
                errors.add("Отгрузка " + dto.externalId() + ": " + ex.getMessage());
                log.error("Не удалось импортировать отгрузку {}: {}", dto.externalId(), ex.getMessage(), ex);
            }
        }

        log.info("Импорт отгрузок (1С): импортировано={}, пропущено={}, с ошибкой={}",
                imported, skipped, errored);
        return new ImportResult(imported, skipped, errored, errors);
    }

    private Optional<ProductReadModel> resolveProduct(UUID organizationId, String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return productRepository.findFirstByOrganizationIdAndNameIgnoreCase(organizationId, name.trim());
    }

    public record ImportResult(int imported, int skipped, int errored, List<String> errors) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("imported", imported);
            m.put("skipped", skipped);
            m.put("errored", errored);
            m.put("errors", errors);
            return m;
        }
    }
}
