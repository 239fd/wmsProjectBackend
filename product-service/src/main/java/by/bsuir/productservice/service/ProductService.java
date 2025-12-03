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
        log.info("Creating product: {}", request.name());

        if (request.sku() != null && productRepository.existsBySku(request.sku())) {
            throw AppException.conflict("Товар с таким SKU уже существует");
        }

        if (request.barcode() != null && productRepository.existsByBarcode(request.barcode())) {
            throw AppException.conflict("Товар с таким штрих-кодом уже существует");
        }

        UUID productId = UUID.randomUUID();

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("name", request.name());
        eventData.put("sku", request.sku());
        eventData.put("barcode", request.barcode());
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
                .name(request.name())
                .sku(request.sku())
                .barcode(request.barcode())
                .category(request.category())
                .description(request.description())
                .unitOfMeasure(request.unitOfMeasure())
                .weightKg(request.weightKg())
                .volumeM3(request.volumeM3())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        productRepository.save(readModel);

        log.info("Product created successfully with ID: {}", productId);
        return mapToResponse(readModel);
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
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}
