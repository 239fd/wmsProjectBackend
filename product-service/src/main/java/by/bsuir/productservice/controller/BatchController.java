package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.CreateBatchRequest;
import by.bsuir.productservice.dto.response.BatchResponse;
import by.bsuir.productservice.service.BatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;




    @PostMapping("/products/{productId}/batches")
    public ResponseEntity<BatchResponse> createBatch(
            @PathVariable UUID productId,
            @Valid @RequestBody CreateBatchRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {


        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }


        CreateBatchRequest updatedRequest = new CreateBatchRequest(
                productId,
                request.batchNumber(),
                request.manufactureDate(),
                request.expiryDate(),
                request.supplier(),
                request.purchasePrice()
        );

        BatchResponse response = batchService.createBatch(updatedRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }




    @GetMapping("/products/{productId}/batches")
    public ResponseEntity<List<BatchResponse>> getBatchesByProduct(@PathVariable UUID productId) {
        List<BatchResponse> response = batchService.getBatchesByProduct(productId);
        return ResponseEntity.ok(response);
    }




    @GetMapping("/batches/{batchId}")
    public ResponseEntity<BatchResponse> getBatch(@PathVariable UUID batchId) {
        BatchResponse response = batchService.getBatch(batchId);
        return ResponseEntity.ok(response);
    }




    @GetMapping("/batches")
    public ResponseEntity<List<BatchResponse>> getAllBatches() {
        List<BatchResponse> response = batchService.getAllBatches();
        return ResponseEntity.ok(response);
    }
}
