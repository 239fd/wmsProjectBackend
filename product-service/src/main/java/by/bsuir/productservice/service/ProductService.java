package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.CreateProductRequest;
import by.bsuir.productservice.dto.request.UpdateProductRequest;
import by.bsuir.productservice.dto.response.ProductResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.ProductEvent;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.repository.ProductEventRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductReadModelRepository productRepository;
    private final ProductEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        return createProduct(request, null);
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, UUID organizationId) {
        log.info("Creating product: {} (org={})", request.name(), organizationId);

        String sku = nullIfBlank(request.sku());
        String barcode = nullIfBlank(request.barcode());

        if (sku == null) {
            sku = generateUniqueSku();
        } else if (productRepository.existsBySku(sku)) {
            throw AppException.conflict("Товар с таким SKU уже существует");
        }
        if (barcode != null && productRepository.existsByBarcode(barcode)) {
            throw AppException.conflict("Товар с таким штрих-кодом уже существует");
        }

        UUID productId = UUID.randomUUID();

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("name", request.name());
        eventData.put("sku", sku);
        eventData.put("barcode", barcode);
        eventData.put("category", request.category());
        eventData.put("unitOfMeasure", request.unitOfMeasure());

        ProductEvent productEvent = ProductEvent.builder()
                .productId(productId)
                .eventType("PRODUCT_CREATED")
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(productEvent);

        ProductReadModel readModel = ProductReadModel.builder()
                .productId(productId)
                .organizationId(organizationId)
                .name(request.name())
                .sku(sku)
                .barcode(barcode)
                .category(request.category())
                .description(request.description())
                .unitOfMeasure(request.unitOfMeasure())
                .weightKg(request.weightKg())
                .volumeM3(request.volumeM3())
                .requiredStorageCondition(request.requiredStorageCondition())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        try {
            productRepository.saveAndFlush(readModel);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
            if (msg.contains("sku")) {
                throw AppException.conflict("Товар с таким SKU уже существует");
            }
            if (msg.contains("barcode")) {
                throw AppException.conflict("Товар с таким штрих-кодом уже существует");
            }
            throw AppException.conflict("Нарушение уникальности при создании товара");
        }

        log.info("Product created successfully with ID: {}", productId);
        return mapToResponse(readModel);
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String generateUniqueSku() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "SKU-" + java.time.Year.now().getValue()
                    + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (!productRepository.existsBySku(candidate)) {
                return candidate;
            }
        }
        return "SKU-" + UUID.randomUUID();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID productId) {
        ProductReadModel product = productRepository.findById(productId)
                .orElseThrow(() -> AppException.notFound("Товар не найден"));
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(String query, Pageable pageable) {
        if (query == null || query.isBlank()) return List.of();
        int limit = pageable != null && pageable.getPageSize() > 0 ? pageable.getPageSize() : 20;
        return productRepository.searchByTextNative(query.trim(), limit).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku) {
        ProductReadModel product = productRepository.findBySku(sku)
                .orElseThrow(() -> AppException.notFound("Товар с таким SKU не найден"));
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductByBarcode(String barcode) {
        ProductReadModel product = productRepository.findByBarcode(barcode)
                .orElseThrow(() -> AppException.notFound("Товар с таким штрих-кодом не найден"));
        return mapToResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(String category) {
        return productRepository.findByCategory(category).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(String category, Pageable pageable) {
        return productRepository.findByCategory(category, pageable).map(this::mapToResponse);
    }

    @Transactional
    public ProductResponse updateProduct(UUID productId, UpdateProductRequest request) {
        log.info("Updating product: {}", productId);

        ProductReadModel product = productRepository.findById(productId)
                .orElseThrow(() -> AppException.notFound("Товар не найден"));

        if (request.sku() != null && !request.sku().equals(product.getSku())) {
            if (productRepository.existsBySku(request.sku())) {
                throw AppException.conflict("Товар с таким SKU уже существует");
            }
        }

        if (request.barcode() != null && !request.barcode().equals(product.getBarcode())) {
            if (productRepository.existsByBarcode(request.barcode())) {
                throw AppException.conflict("Товар с таким штрих-кодом уже существует");
            }
        }

        Map<String, Object> eventData = new HashMap<>();
        if (request.name() != null) eventData.put("name", request.name());
        if (request.sku() != null) eventData.put("sku", request.sku());
        if (request.barcode() != null) eventData.put("barcode", request.barcode());
        if (request.category() != null) eventData.put("category", request.category());
        if (request.description() != null) eventData.put("description", request.description());
        if (request.unitOfMeasure() != null) eventData.put("unitOfMeasure", request.unitOfMeasure());
        if (request.weightKg() != null) eventData.put("weightKg", request.weightKg());
        if (request.volumeM3() != null) eventData.put("volumeM3", request.volumeM3());
        if (request.requiredStorageCondition() != null) eventData.put("requiredStorageCondition", request.requiredStorageCondition().name());

        ProductEvent productEvent = ProductEvent.builder()
                .productId(productId)
                .eventType("PRODUCT_UPDATED")
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(productEvent);

        if (request.name() != null) product.setName(request.name());
        if (request.sku() != null) product.setSku(request.sku());
        if (request.barcode() != null) product.setBarcode(request.barcode());
        if (request.category() != null) product.setCategory(request.category());
        if (request.description() != null) product.setDescription(request.description());
        if (request.unitOfMeasure() != null) product.setUnitOfMeasure(request.unitOfMeasure());
        if (request.weightKg() != null) product.setWeightKg(request.weightKg());
        if (request.volumeM3() != null) product.setVolumeM3(request.volumeM3());
        if (request.requiredStorageCondition() != null) product.setRequiredStorageCondition(request.requiredStorageCondition());
        product.setUpdatedAt(LocalDateTime.now());

        productRepository.save(product);

        log.info("Product updated successfully: {}", productId);
        return mapToResponse(product);
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        log.info("Deleting product: {}", productId);

        ProductReadModel product = productRepository.findById(productId)
                .orElseThrow(() -> AppException.notFound("Товар не найден"));

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("productId", productId.toString());

        ProductEvent productEvent = ProductEvent.builder()
                .productId(productId)
                .eventType("PRODUCT_DELETED")
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(productEvent);

        productRepository.delete(product);
        log.info("Product deleted successfully: {}", productId);
    }

    private ProductResponse mapToResponse(ProductReadModel model) {
        return new ProductResponse(
                model.getProductId(),
                model.getName(),
                model.getSku(),
                model.getBarcode(),
                model.getCategory(),
                model.getDescription(),
                model.getUnitOfMeasure(),
                model.getWeightKg(),
                model.getVolumeM3(),
                model.getRequiredStorageCondition(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}
