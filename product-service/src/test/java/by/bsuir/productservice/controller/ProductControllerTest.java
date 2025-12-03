package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.CreateProductRequest;
import by.bsuir.productservice.dto.request.UpdateProductRequest;
import by.bsuir.productservice.dto.response.ProductResponse;
import by.bsuir.productservice.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductController Unit Tests")
class ProductControllerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    @Test
    @DisplayName("createProduct: Given ADMIN role Should create and return 201")
    void createProduct_GivenAdminRole_ShouldCreateAndReturn201() {
        CreateProductRequest request = new CreateProductRequest(
                "Test Product",
                "SKU-001",
                "1234567890",
                "Electronics",
                "Test description",
                "pcs",
                new BigDecimal("1.5"),
                new BigDecimal("0.01")
        );

        ProductResponse expectedResponse = new ProductResponse(
                UUID.randomUUID(),
                "Test Product",
                "SKU-001",
                "1234567890",
                "Electronics",
                "Test description",
                "pcs",
                new BigDecimal("1.5"),
                new BigDecimal("0.01"),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(expectedResponse);

        ResponseEntity<ProductResponse> response = productController.createProduct(request, "ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Test Product");
        verify(productService, times(1)).createProduct(any(CreateProductRequest.class));
    }

    @Test
    @DisplayName("createProduct: Given no role Should return 403")
    void createProduct_GivenNoRole_ShouldReturn403() {
        CreateProductRequest request = new CreateProductRequest(
                "Test Product",
                "SKU-001",
                "1234567890",
                "Electronics",
                "Test description",
                "pcs",
                new BigDecimal("1.5"),
                new BigDecimal("0.01")
        );

        ResponseEntity<ProductResponse> response = productController.createProduct(request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(productService, never()).createProduct(any(CreateProductRequest.class));
    }

    @Test
    @DisplayName("getProduct: Should return product by ID")
    void getProduct_ShouldReturnProductById() {
        UUID productId = UUID.randomUUID();
        ProductResponse expectedResponse = new ProductResponse(
                productId,
                "Test Product",
                "SKU-001",
                "1234567890",
                "Electronics",
                "Test description",
                "pcs",
                new BigDecimal("1.5"),
                new BigDecimal("0.01"),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(productService.getProduct(productId)).thenReturn(expectedResponse);

        ResponseEntity<ProductResponse> response = productController.getProduct(productId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().productId()).isEqualTo(productId);
        verify(productService, times(1)).getProduct(productId);
    }

    @Test
    @DisplayName("getAllProducts: Should return all products")
    void getAllProducts_ShouldReturnAllProducts() {
        ProductResponse product1 = new ProductResponse(
                UUID.randomUUID(),
                "Product 1",
                "SKU-001",
                "1234567890",
                "Electronics",
                "Description 1",
                "pcs",
                new BigDecimal("1.5"),
                new BigDecimal("0.01"),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(productService.getAllProducts()).thenReturn(List.of(product1));

        ResponseEntity<List<ProductResponse>> response = productController.getAllProducts();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).name()).isEqualTo("Product 1");
        verify(productService, times(1)).getAllProducts();
    }

    @Test
    @DisplayName("getProductBySku: Should return product by SKU")
    void getProductBySku_ShouldReturnProductBySku() {
        ProductResponse expectedResponse = new ProductResponse(
                UUID.randomUUID(),
                "Test Product",
                "SKU-001",
                "1234567890",
                "Electronics",
                "Test description",
                "pcs",
                new BigDecimal("1.5"),
                new BigDecimal("0.01"),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(productService.getProductBySku("SKU-001")).thenReturn(expectedResponse);

        ResponseEntity<ProductResponse> response = productController.getProductBySku("SKU-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().sku()).isEqualTo("SKU-001");
        verify(productService, times(1)).getProductBySku("SKU-001");
    }

    @Test
    @DisplayName("getProductByBarcode: Should return product by barcode")
    void getProductByBarcode_ShouldReturnProductByBarcode() {
        ProductResponse expectedResponse = new ProductResponse(
                UUID.randomUUID(),
                "Test Product",
                "SKU-001",
                "1234567890",
                "Electronics",
                "Test description",
                "pcs",
                new BigDecimal("1.5"),
                new BigDecimal("0.01"),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(productService.getProductByBarcode("1234567890")).thenReturn(expectedResponse);

        ResponseEntity<ProductResponse> response = productController.getProductByBarcode("1234567890");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().barcode()).isEqualTo("1234567890");
        verify(productService, times(1)).getProductByBarcode("1234567890");
    }

    @Test
    @DisplayName("getProductsByCategory: Should return products by category")
    void getProductsByCategory_ShouldReturnProductsByCategory() {
        ProductResponse product1 = new ProductResponse(
                UUID.randomUUID(),
                "Product 1",
                "SKU-001",
                "1234567890",
                "Electronics",
                "Description 1",
                "pcs",
                new BigDecimal("1.5"),
                new BigDecimal("0.01"),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(productService.getProductsByCategory("Electronics")).thenReturn(List.of(product1));

        ResponseEntity<List<ProductResponse>> response = productController.getProductsByCategory("Electronics");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).category()).isEqualTo("Electronics");
        verify(productService, times(1)).getProductsByCategory("Electronics");
    }

    @Test
    @DisplayName("updateProduct: Given ADMIN role Should update and return 200")
    void updateProduct_GivenAdminRole_ShouldUpdateAndReturn200() {
        UUID productId = UUID.randomUUID();
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Product",
                "SKU-001",
                "1234567890",
                "Updated Category",
                "Updated description",
                "pcs",
                new BigDecimal("2.0"),
                new BigDecimal("0.02")
        );

        ProductResponse expectedResponse = new ProductResponse(
                productId,
                "Updated Product",
                "SKU-001",
                "1234567890",
                "Updated Category",
                "Updated description",
                "pcs",
                new BigDecimal("2.0"),
                new BigDecimal("0.02"),
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(productService.updateProduct(eq(productId), any(UpdateProductRequest.class))).thenReturn(expectedResponse);

        ResponseEntity<ProductResponse> response = productController.updateProduct(productId, request, "ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Updated Product");
        verify(productService, times(1)).updateProduct(eq(productId), any(UpdateProductRequest.class));
    }

    @Test
    @DisplayName("updateProduct: Given no role Should return 403")
    void updateProduct_GivenNoRole_ShouldReturn403() {
        UUID productId = UUID.randomUUID();
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Product",
                "SKU-001",
                "1234567890",
                "Updated Category",
                "Updated description",
                "pcs",
                new BigDecimal("2.0"),
                new BigDecimal("0.02")
        );

        ResponseEntity<ProductResponse> response = productController.updateProduct(productId, request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(productService, never()).updateProduct(any(), any());
    }

    @Test
    @DisplayName("deleteProduct: Given DIRECTOR role Should delete and return 200")
    void deleteProduct_GivenDirectorRole_ShouldDeleteAndReturn200() {
        UUID productId = UUID.randomUUID();
        doNothing().when(productService).deleteProduct(productId);

        ResponseEntity<Map<String, String>> response = productController.deleteProduct(productId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        verify(productService, times(1)).deleteProduct(productId);
    }

    @Test
    @DisplayName("deleteProduct: Given non-DIRECTOR role Should return 403")
    void deleteProduct_GivenNonDirectorRole_ShouldReturn403() {
        UUID productId = UUID.randomUUID();

        ResponseEntity<Map<String, String>> response = productController.deleteProduct(productId, "ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(productService, never()).deleteProduct(any());
    }
}

