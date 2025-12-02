package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.CreateProductRequest;
import by.bsuir.productservice.dto.request.UpdateProductRequest;
import by.bsuir.productservice.dto.response.ProductResponse;
import by.bsuir.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;




    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {


        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }




    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID productId) {
        ProductResponse response = productService.getProduct(productId);
        return ResponseEntity.ok(response);
    }




    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> response = productService.getAllProducts();
        return ResponseEntity.ok(response);
    }




    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductResponse> getProductBySku(@PathVariable String sku) {
        ProductResponse response = productService.getProductBySku(sku);
        return ResponseEntity.ok(response);
    }




    @GetMapping("/barcode/{barcode}")
    public ResponseEntity<ProductResponse> getProductByBarcode(@PathVariable String barcode) {
        ProductResponse response = productService.getProductByBarcode(barcode);
        return ResponseEntity.ok(response);
    }




    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable String category) {
        List<ProductResponse> response = productService.getProductsByCategory(category);
        return ResponseEntity.ok(response);
    }




    @PutMapping("/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ProductResponse response = productService.updateProduct(productId, request);
        return ResponseEntity.ok(response);
    }




    @DeleteMapping("/{productId}")
    public ResponseEntity<Map<String, String>> deleteProduct(
            @PathVariable UUID productId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole != null && !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        productService.deleteProduct(productId);
        return ResponseEntity.ok(Map.of("message", "Товар успешно удалён"));
    }
}
