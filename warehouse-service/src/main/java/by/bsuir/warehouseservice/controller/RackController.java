package by.bsuir.warehouseservice.controller;

import by.bsuir.warehouseservice.dto.request.*;
import by.bsuir.warehouseservice.dto.response.RackResponse;
import by.bsuir.warehouseservice.service.RackService;
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
@RequestMapping("/api/racks")
@RequiredArgsConstructor
public class RackController {

    private final RackService rackService;




    @PostMapping
    public ResponseEntity<RackResponse> createRack(
            @Valid @RequestBody CreateRackRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        RackResponse response = rackService.createRack(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }




    @PostMapping("/{rackId}/shelves")
    public ResponseEntity<Map<String, String>> createShelf(
            @PathVariable UUID rackId,
            @Valid @RequestBody CreateShelfRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }


        CreateShelfRequest updatedRequest = new CreateShelfRequest(
                rackId,
                request.shelfCapacityKg(),
                request.lengthCm(),
                request.widthCm(),
                request.heightCm()
        );

        rackService.createShelf(updatedRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Полка успешно создана"));
    }




    @PostMapping("/{rackId}/cells")
    public ResponseEntity<Map<String, String>> createCell(
            @PathVariable UUID rackId,
            @Valid @RequestBody CreateCellRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        CreateCellRequest updatedRequest = new CreateCellRequest(
                rackId,
                request.maxWeightKg(),
                request.lengthCm(),
                request.widthCm(),
                request.heightCm()
        );

        rackService.createCell(updatedRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Ячейка успешно создана"));
    }




    @PostMapping("/{rackId}/fridges")
    public ResponseEntity<Map<String, String>> createFridge(
            @PathVariable UUID rackId,
            @Valid @RequestBody CreateFridgeRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        CreateFridgeRequest updatedRequest = new CreateFridgeRequest(
                rackId,
                request.temperatureC(),
                request.lengthCm(),
                request.widthCm(),
                request.heightCm()
        );

        rackService.createFridge(updatedRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Холодильник успешно создан"));
    }




    @PostMapping("/{rackId}/pallets")
    public ResponseEntity<Map<String, String>> createPallet(
            @PathVariable UUID rackId,
            @Valid @RequestBody CreatePalletRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        CreatePalletRequest updatedRequest = new CreatePalletRequest(
                rackId,
                request.palletPlaceCount(),
                request.maxWeightKg()
        );

        rackService.createPallet(updatedRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Паллет успешно создан"));
    }




    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<RackResponse>> getRacksByWarehouse(@PathVariable UUID warehouseId) {
        List<RackResponse> response = rackService.getRacksByWarehouse(warehouseId);
        return ResponseEntity.ok(response);
    }




    @GetMapping("/{rackId}")
    public ResponseEntity<RackResponse> getRack(@PathVariable UUID rackId) {
        RackResponse response = rackService.getRack(rackId);
        return ResponseEntity.ok(response);
    }




    @GetMapping("/cells/{cellId}")
    public ResponseEntity<Object> getCellInfo(@PathVariable UUID cellId) {
        Object response = rackService.getCellInfo(cellId);
        return ResponseEntity.ok(response);
    }




    @GetMapping("/{rackId}/cells")
    public ResponseEntity<List<Object>> getCellsByRack(@PathVariable UUID rackId) {
        List<Object> response = rackService.getCellsByRack(rackId);
        return ResponseEntity.ok(response);
    }




    @DeleteMapping("/{rackId}")
    public ResponseEntity<Map<String, String>> deleteRack(
            @PathVariable UUID rackId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        rackService.deleteRack(rackId);
        return ResponseEntity.ok(Map.of("message", "Стеллаж успешно удалён"));
    }
}
