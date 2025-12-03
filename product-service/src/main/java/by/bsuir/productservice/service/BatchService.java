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
        log.info("Creating batch for product: {}", request.productId());

        ProductReadModel product = productRepository.findById(request.productId())
                .orElseThrow(() -> AppException.notFound("Товар не найден"));

        ProductBatch batch = ProductBatch.builder()
                .batchId(UUID.randomUUID())
                .productId(request.productId())
                .batchNumber(request.batchNumber())
                .manufactureDate(request.manufactureDate())
                .expiryDate(request.expiryDate())
                .supplier(request.supplier())
                .purchasePrice(request.purchasePrice())
                .createdAt(LocalDateTime.now())
                .build();

        batchRepository.save(batch);

        log.info("Batch created successfully with ID: {}", batch.getBatchId());
        return mapToResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getBatchesByProduct(UUID productId) {
        return batchRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatch(UUID batchId) {
        ProductBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> AppException.notFound("Партия не найдена"));
        return mapToResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> getAllBatches() {
        return batchRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private BatchResponse mapToResponse(ProductBatch batch) {
        return new BatchResponse(
                batch.getBatchId(),
                batch.getProductId(),
                batch.getBatchNumber(),
                batch.getManufactureDate(),
                batch.getExpiryDate(),
                batch.getSupplier(),
                batch.getPurchasePrice(),
                batch.getCreatedAt()
        );
    }
}
