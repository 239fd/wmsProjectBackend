package by.bsuir.productservice.integration;

import by.bsuir.productservice.controller.ProductController;
import by.bsuir.productservice.dto.request.CreateProductRequest;
import by.bsuir.productservice.dto.response.ProductResponse;
import by.bsuir.productservice.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для ProductController.
 *
 * Используем standalone MockMvc setup без поднятия Spring контекста,
 * что позволяет избежать проблем с JPA, RabbitMQ и Loki зависимостями.
 *
 * Тестируют веб-слой:
 * - HTTP маршрутизация
 * - Сериализация/десериализация JSON
 * - Проверка ролей (X-User-Role)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductController Integration Tests")
class ProductControllerIntegrationTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    private static final String BASE_URL = "/api/products";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(productController)
                .build();
    }

    @Nested
    @DisplayName("POST /api/products - Создание товара")
    class CreateProductTests {

        @Test
        @DisplayName("С ролью ADMIN - успешное создание, возвращает 201")
        void createProduct_WithAdminRole_ShouldReturnCreated() throws Exception {
            // Given
            CreateProductRequest request = new CreateProductRequest(
                    "Тестовый товар",
                    "SKU-001",
                    "1234567890123",
                    "Электроника",
                    "Описание товара",
                    "шт",
                    new BigDecimal("1.5"),
                    new BigDecimal("0.01")
            );

            ProductResponse response = new ProductResponse(
                    UUID.randomUUID(),
                    "Тестовый товар",
                    "SKU-001",
                    "1234567890123",
                    "Электроника",
                    "Описание товара",
                    "шт",
                    new BigDecimal("1.5"),
                    new BigDecimal("0.01"),
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );

            when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(response);

            // When & Then
            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Тестовый товар"))
                    .andExpect(jsonPath("$.sku").value("SKU-001"))
                    .andExpect(jsonPath("$.category").value("Электроника"));

            verify(productService).createProduct(any(CreateProductRequest.class));
        }

        @Test
        @DisplayName("С ролью DIRECTOR - успешное создание")
        void createProduct_WithDirectorRole_ShouldReturnCreated() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                    "Товар директора", "SKU-DIR", "999", "Категория",
                    "Описание", "шт", BigDecimal.ONE, BigDecimal.ZERO
            );

            ProductResponse response = new ProductResponse(
                    UUID.randomUUID(), "Товар директора", "SKU-DIR", "999", "Категория",
                    "Описание", "шт", BigDecimal.ONE, BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(productService.createProduct(any())).thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Role", "DIRECTOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("С ролью WORKER - успешное создание")
        void createProduct_WithWorkerRole_ShouldReturnCreated() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                    "Товар", "SKU-W", "888", "Категория",
                    "Описание", "шт", BigDecimal.ONE, BigDecimal.ZERO
            );

            ProductResponse response = new ProductResponse(
                    UUID.randomUUID(), "Товар", "SKU-W", "888", "Категория",
                    "Описание", "шт", BigDecimal.ONE, BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(productService.createProduct(any())).thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Role", "WORKER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Без роли - возвращает 403 Forbidden")
        void createProduct_WithoutRole_ShouldReturnForbidden() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                    "Тестовый товар", "SKU-001", "1234567890123", "Электроника",
                    "Описание", "шт", new BigDecimal("1.5"), new BigDecimal("0.01")
            );

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(productService, never()).createProduct(any());
        }

        @Test
        @DisplayName("Пустое название - возвращает 400")
        void createProduct_WithEmptyName_ShouldReturnBadRequest() throws Exception {
            CreateProductRequest request = new CreateProductRequest(
                    "", "SKU-001", "1234567890123", "Электроника",
                    "Описание", "шт", new BigDecimal("1.5"), new BigDecimal("0.01")
            );

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Слишком длинное название - возвращает 400")
        void createProduct_WithTooLongName_ShouldReturnBadRequest() throws Exception {
            String longName = "A".repeat(300);
            CreateProductRequest request = new CreateProductRequest(
                    longName, "SKU", "123", "Cat", "Desc", "шт", BigDecimal.ONE, BigDecimal.ZERO
            );

            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/products - Получение товаров")
    class GetProductsTests {

        @Test
        @DisplayName("Получение товара по ID - возвращает 200")
        void getProduct_ById_ShouldReturnOk() throws Exception {
            UUID productId = UUID.randomUUID();
            ProductResponse response = new ProductResponse(
                    productId, "Товар", "SKU-001", "1234567890", "Категория",
                    "Описание", "шт", new BigDecimal("1.0"), new BigDecimal("0.01"),
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(productService.getProduct(productId)).thenReturn(response);

            mockMvc.perform(get(BASE_URL + "/{productId}", productId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.name").value("Товар"));
        }

        @Test
        @DisplayName("Получение всех товаров - возвращает список")
        void getAllProducts_ShouldReturnList() throws Exception {
            List<ProductResponse> products = List.of(
                    new ProductResponse(UUID.randomUUID(), "Товар 1", "SKU-001", "123",
                            "Категория", "Описание", "шт", BigDecimal.ONE, BigDecimal.ZERO,
                            LocalDateTime.now(), LocalDateTime.now()),
                    new ProductResponse(UUID.randomUUID(), "Товар 2", "SKU-002", "456",
                            "Категория", "Описание", "шт", BigDecimal.ONE, BigDecimal.ZERO,
                            LocalDateTime.now(), LocalDateTime.now())
            );

            when(productService.getAllProducts()).thenReturn(products);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("Товар 1"))
                    .andExpect(jsonPath("$[1].name").value("Товар 2"));
        }

        @Test
        @DisplayName("Получение товара по SKU - возвращает 200")
        void getProductBySku_ShouldReturnOk() throws Exception {
            ProductResponse response = new ProductResponse(
                    UUID.randomUUID(), "Товар", "SKU-TEST", "123", "Категория",
                    "Описание", "шт", BigDecimal.ONE, BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(productService.getProductBySku("SKU-TEST")).thenReturn(response);

            mockMvc.perform(get(BASE_URL + "/sku/{sku}", "SKU-TEST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sku").value("SKU-TEST"));
        }

        @Test
        @DisplayName("Получение товара по штрих-коду - возвращает 200")
        void getProductByBarcode_ShouldReturnOk() throws Exception {
            ProductResponse response = new ProductResponse(
                    UUID.randomUUID(), "Товар", "SKU-001", "9876543210", "Категория",
                    "Описание", "шт", BigDecimal.ONE, BigDecimal.ZERO,
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(productService.getProductByBarcode("9876543210")).thenReturn(response);

            mockMvc.perform(get(BASE_URL + "/barcode/{barcode}", "9876543210"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.barcode").value("9876543210"));
        }

        @Test
        @DisplayName("Получение товаров по категории - возвращает список")
        void getProductsByCategory_ShouldReturnList() throws Exception {
            List<ProductResponse> products = List.of(
                    new ProductResponse(UUID.randomUUID(), "Электроника 1", "SKU-E1", "123",
                            "Электроника", "Описание", "шт", BigDecimal.ONE, BigDecimal.ZERO,
                            LocalDateTime.now(), LocalDateTime.now())
            );

            when(productService.getProductsByCategory("Электроника")).thenReturn(products);

            mockMvc.perform(get(BASE_URL + "/category/{category}", "Электроника"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].category").value("Электроника"));
        }
    }

    @Nested
    @DisplayName("Тестирование JSON сериализации")
    class JsonSerializationTests {

        @Test
        @DisplayName("ProductResponse содержит все поля")
        void productResponse_ShouldContainAllFields() throws Exception {
            UUID productId = UUID.randomUUID();
            ProductResponse response = new ProductResponse(
                    productId, "Товар", "SKU-001", "123456789", "Категория",
                    "Описание товара", "шт", new BigDecimal("2.5"), new BigDecimal("0.05"),
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(productService.getProduct(productId)).thenReturn(response);

            mockMvc.perform(get(BASE_URL + "/{productId}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").exists())
                    .andExpect(jsonPath("$.name").exists())
                    .andExpect(jsonPath("$.sku").exists())
                    .andExpect(jsonPath("$.barcode").exists())
                    .andExpect(jsonPath("$.category").exists())
                    .andExpect(jsonPath("$.description").exists())
                    .andExpect(jsonPath("$.unitOfMeasure").exists())
                    .andExpect(jsonPath("$.weightKg").exists())
                    .andExpect(jsonPath("$.volumeM3").exists())
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.updatedAt").exists());
        }
    }
}
