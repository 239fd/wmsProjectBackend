package by.bsuir.documentservice.controller;

import by.bsuir.documentservice.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;





@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;




    @GetMapping("/{documentId}")
    public ResponseEntity<byte[]> getDocument(@PathVariable UUID documentId) {
        log.info("GET /api/documents/{}", documentId);

        byte[] document = documentService.getDocument(documentId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", documentId + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(document);
    }




    @GetMapping("/{documentId}/metadata")
    public ResponseEntity<Map<String, Object>> getDocumentMetadata(@PathVariable UUID documentId) {
        log.info("GET /api/documents/{}/metadata", documentId);

        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        return ResponseEntity.ok(metadata);
    }




    @PostMapping("/receipt-order")
    public ResponseEntity<Map<String, String>> generateReceiptOrder(@RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/receipt-order");

        UUID documentId = documentService.generateReceiptOrder(data);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "documentId", documentId.toString(),
                "type", "receipt-order",
                "status", "stub",
                "message", "Receipt order generation is not fully implemented yet"
        ));
    }




    @PostMapping("/shipment-order")
    public ResponseEntity<Map<String, String>> generateShipmentOrder(@RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/shipment-order");

        UUID documentId = documentService.generateShipmentOrder(data);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "documentId", documentId.toString(),
                "type", "shipment-order",
                "status", "stub",
                "message", "Shipment order generation is not fully implemented yet"
        ));
    }




    @PostMapping("/inventory-report")
    public ResponseEntity<Map<String, String>> generateInventoryReport(@RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/inventory-report");

        UUID documentId = documentService.generateInventoryReport(data);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "documentId", documentId.toString(),
                "type", "inventory-report",
                "status", "stub",
                "message", "Inventory report generation is not fully implemented yet"
        ));
    }




    @PostMapping("/revaluation-act")
    public ResponseEntity<Map<String, String>> generateRevaluationAct(@RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/revaluation-act");

        UUID documentId = documentService.generateRevaluationAct(data);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "documentId", documentId.toString(),
                "type", "revaluation-act",
                "status", "stub",
                "message", "Revaluation act generation is not fully implemented yet (RPA)"
        ));
    }




    @PostMapping("/write-off-act")
    public ResponseEntity<Map<String, String>> generateWriteOffAct(@RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/write-off-act");

        UUID documentId = documentService.generateWriteOffAct(data);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "documentId", documentId.toString(),
                "type", "write-off-act",
                "status", "stub",
                "message", "Write-off act generation is not fully implemented yet"
        ));
    }




    @PostMapping("/waybill")
    public ResponseEntity<Map<String, String>> generateWaybill(@RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/waybill");

        UUID documentId = documentService.generateWaybill(data);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "documentId", documentId.toString(),
                "type", "waybill",
                "status", "stub",
                "message", "Waybill generation is not fully implemented yet"
        ));
    }




    @PostMapping("/picking-list")
    public ResponseEntity<Map<String, String>> generatePickingList(@RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/picking-list");

        UUID documentId = documentService.generatePickingList(data);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "documentId", documentId.toString(),
                "type", "picking-list",
                "status", "stub",
                "message", "Picking list generation is not fully implemented yet"
        ));
    }




    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/documents?page={}&size={}", page, size);

        Map<String, Object> result = documentService.getAllDocuments(page, size);
        return ResponseEntity.ok(result);
    }




    @GetMapping("/stub-info")
    public ResponseEntity<Map<String, Object>> getStubInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "Document Service");
        info.put("status", "STUB");
        info.put("version", "0.1.0-SNAPSHOT");
        info.put("message", "Document Service is not fully implemented yet");
        info.put("todo", new String[]{
                "Implement PDF generation",
                "Add RPA integration for automatic form filling",
                "Create document templates",
                "Add document storage (database or file system)",
                "Implement electronic signature",
                "Add document versioning",
                "Implement document workflow (draft -> approved -> archived)"
        });
        info.put("availableEndpoints", new String[]{
                "GET /api/documents/{id}",
                "GET /api/documents/{id}/metadata",
                "GET /api/documents",
                "POST /api/documents/receipt-order",
                "POST /api/documents/shipment-order",
                "POST /api/documents/inventory-report",
                "POST /api/documents/revaluation-act",
                "POST /api/documents/write-off-act",
                "POST /api/documents/waybill",
                "POST /api/documents/picking-list"
        });

        return ResponseEntity.ok(info);
    }
}
