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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Tests")
class ProductServiceTest {

    @Mock
    private ProductReadModelRepository productRepository;

    @Mock
    private ProductEventRepository eventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProductService productService;

    private UUID productId;
    private ProductReadModel product;
    private CreateProductRequest createRequest;
    private UpdateProductRequest updateRequest;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();

        product = ProductReadModel.builder()
                .productId(productId)
                .name("Test Product")
                .sku("SKU-001")
                .barcode("1234567890")
                .category("Electronics")
                .description("Test description")
                .unitOfMeasure("pcs")
                .weightKg(new BigDecimal("1.5"))
                .volumeM3(new BigDecimal("0.01"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        createRequest = new CreateProductRequest(
                "Test Product",
                "SKU-001",
                "1234567890",
                "Electronics",
                "Test description",
                "pcs",
                new BigDecimal("1.5"),
                new BigDecimal("0.01")
        );

        updateRequest = new UpdateProductRequest(
                "Updated Product",
                "SKU-001",
                "1234567890",
                "Updated Category",
                "Updated description",
                "pcs",
                new BigDecimal("2.0"),
                new BigDecimal("0.02")
        );
    }

    @Test
    @DisplayName("Should create product successfully")
    void shouldCreateProductSuccessfully() {
        when(productRepository.existsBySku("SKU-001")).thenReturn(false);
        when(productRepository.existsByBarcode("1234567890")).thenReturn(false);
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(ProductEvent.class))).thenReturn(null);
        when(productRepository.save(any(ProductReadModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.createProduct(createRequest);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Test Product");
        assertThat(response.sku()).isEqualTo("SKU-001");
        verify(productRepository, times(1)).existsBySku("SKU-001");
        verify(productRepository, times(1)).existsByBarcode("1234567890");
        verify(productRepository, times(1)).save(any(ProductReadModel.class));
        verify(eventRepository, times(1)).save(any(ProductEvent.class));
    }

    @Test
    @DisplayName("Should throw exception when SKU already exists")
    void shouldThrowExceptionWhenSkuExists() {
        when(productRepository.existsBySku("SKU-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(createRequest))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("SKU");

        verify(productRepository, times(1)).existsBySku("SKU-001");
        verify(productRepository, never()).save(any(ProductReadModel.class));
    }

    @Test
    @DisplayName("Should throw exception when barcode already exists")
    void shouldThrowExceptionWhenBarcodeExists() {
        when(productRepository.existsBySku("SKU-001")).thenReturn(false);
        when(productRepository.existsByBarcode("1234567890")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(createRequest))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("штрих-кодом");

        verify(productRepository, times(1)).existsByBarcode("1234567890");
        verify(productRepository, never()).save(any(ProductReadModel.class));
    }

    @Test
    @DisplayName("Should get product by ID successfully")
    void shouldGetProductByIdSuccessfully() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProduct(productId);

        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.name()).isEqualTo("Test Product");
        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    @DisplayName("Should throw exception when product not found by ID")
    void shouldThrowExceptionWhenProductNotFoundById() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(productId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");

        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    @DisplayName("Should get all products successfully")
    void shouldGetAllProductsSuccessfully() {
        ProductReadModel product2 = ProductReadModel.builder()
                .productId(UUID.randomUUID())
                .name("Product 2")
                .sku("SKU-002")
                .build();

        when(productRepository.findAll()).thenReturn(Arrays.asList(product, product2));

        List<ProductResponse> response = productService.getAllProducts();

        assertThat(response).hasSize(2);
        assertThat(response.get(0).name()).isEqualTo("Test Product");
        assertThat(response.get(1).name()).isEqualTo("Product 2");
        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should get product by SKU successfully")
    void shouldGetProductBySkuSuccessfully() {
        when(productRepository.findBySku("SKU-001")).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProductBySku("SKU-001");

        assertThat(response).isNotNull();
        assertThat(response.sku()).isEqualTo("SKU-001");
        verify(productRepository, times(1)).findBySku("SKU-001");
    }

    @Test
    @DisplayName("Should throw exception when product not found by SKU")
    void shouldThrowExceptionWhenProductNotFoundBySku() {
        when(productRepository.findBySku("SKU-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductBySku("SKU-999"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("SKU");

        verify(productRepository, times(1)).findBySku("SKU-999");
    }

    @Test
    @DisplayName("Should get product by barcode successfully")
    void shouldGetProductByBarcodeSuccessfully() {
        when(productRepository.findByBarcode("1234567890")).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProductByBarcode("1234567890");

        assertThat(response).isNotNull();
        assertThat(response.barcode()).isEqualTo("1234567890");
        verify(productRepository, times(1)).findByBarcode("1234567890");
    }

    @Test
    @DisplayName("Should throw exception when product not found by barcode")
    void shouldThrowExceptionWhenProductNotFoundByBarcode() {
        when(productRepository.findByBarcode("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductByBarcode("999"))
                .isInstanceOf(AppException.class);

        verify(productRepository, times(1)).findByBarcode("999");
    }

    @Test
    @DisplayName("Should get products by category successfully")
    void shouldGetProductsByCategorySuccessfully() {
        when(productRepository.findByCategory("Electronics")).thenReturn(Arrays.asList(product));

        List<ProductResponse> response = productService.getProductsByCategory("Electronics");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).category()).isEqualTo("Electronics");
        verify(productRepository, times(1)).findByCategory("Electronics");
    }

    @Test
    @DisplayName("Should update product successfully")
    void shouldUpdateProductSuccessfully() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(ProductEvent.class))).thenReturn(null);
        when(productRepository.save(any(ProductReadModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.updateProduct(productId, updateRequest);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Updated Product");
        verify(productRepository, times(1)).findById(productId);
        verify(productRepository, times(1)).save(any(ProductReadModel.class));
        verify(eventRepository, times(1)).save(any(ProductEvent.class));
    }

    @Test
    @DisplayName("Should delete product successfully")
    void shouldDeleteProductSuccessfully() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(ProductEvent.class))).thenReturn(null);

        productService.deleteProduct(productId);

        verify(productRepository, times(1)).findById(productId);
        verify(productRepository, times(1)).delete(product);
        verify(eventRepository, times(1)).save(any(ProductEvent.class));
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent product")
    void shouldThrowExceptionWhenDeletingNonExistentProduct() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(productId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");

        verify(productRepository, times(1)).findById(productId);
        verify(productRepository, never()).delete(any(ProductReadModel.class));
        verify(eventRepository, never()).save(any(ProductEvent.class));
    }
}

