package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateBatchRequest;
import by.bsuir.productservice.dto.response.BatchResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchService {

    private final ProductBatchRepository batchRepository;
    private final ProductReadModelRepository productRepository;

    @Transactional
    public BatchResponse createBatch(CreateBatchRequest request) {
        return createBatch(request, null);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getBatchesByProduct(UUID productId) {
        return getBatchesByProduct(productId, null);
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatch(UUID batchId) {
        return getBatch(batchId, null);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getAllBatches() {
        return getAllBatches(null);
    }

    @Transactional
    public BatchResponse createBatch(CreateBatchRequest request, UUID organizationId) {
        log.info("Creating batch for product: {} (org: {})", request.productId(), organizationId);

        ProductReadModel product = productRepository.findById(request.productId())
                .orElseThrow(() -> AppException.notFound("Товар не найден"));

        UUID effectiveOrgId = organizationId != null ? organizationId : product.getOrganizationId();

        ProductBatch batch = ProductBatch.builder()
                .batchId(UUID.randomUUID())
                .productId(request.productId())
                .organizationId(effectiveOrgId)
                .supplyId(request.supplyId())
                .batchNumber(request.batchNumber())
                .manufactureDate(request.manufactureDate())
                .expiryDate(request.expiryDate())
                .supplier(request.supplier())
                .purchasePrice(request.purchasePrice())
                .storageConditions(request.storageConditions())
                .createdAt(LocalDateTime.now())
                .build();

        batchRepository.save(batch);

        log.info("Batch created successfully with ID: {}", batch.getBatchId());
        return mapToResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getBatchesByProduct(UUID productId, UUID organizationId) {
        List<ProductBatch> batches = (organizationId != null)
                ? batchRepository.findByOrganizationIdAndProductId(organizationId, productId)
                : batchRepository.findByProductIdOrderByCreatedAtDesc(productId);
        return batches.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<BatchResponse> getBatchesByProduct(UUID productId, UUID organizationId, Pageable pageable) {
        Page<ProductBatch> batches = (organizationId != null)
                ? batchRepository.findByOrganizationIdAndProductId(organizationId, productId, pageable)
                : batchRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable);
        return batches.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatch(UUID batchId, UUID organizationId) {
        ProductBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> AppException.notFound("Партия не найдена"));
        if (organizationId != null && batch.getOrganizationId() != null
                && !organizationId.equals(batch.getOrganizationId())) {
            throw AppException.forbidden("Партия принадлежит другой организации");
        }
        return mapToResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getAllBatches(UUID organizationId) {
        List<ProductBatch> batches = (organizationId != null)
                ? batchRepository.findByOrganizationId(organizationId)
                : batchRepository.findAll();
        return batches.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<BatchResponse> getAllBatches(UUID organizationId, Pageable pageable) {
        Page<ProductBatch> batches = (organizationId != null)
                ? batchRepository.findByOrganizationId(organizationId, pageable)
                : batchRepository.findAll(pageable);
        return batches.map(this::mapToResponse);
    }

    private BatchResponse mapToResponse(ProductBatch batch) {
        return new BatchResponse(
                batch.getBatchId(),
                batch.getProductId(),
                batch.getOrganizationId(),
                batch.getSupplyId(),
                batch.getBatchNumber(),
                batch.getManufactureDate(),
                batch.getExpiryDate(),
                batch.getSupplier(),
                batch.getPurchasePrice(),
                batch.getStorageConditions(),
                batch.getCreatedAt()
        );
    }
}
