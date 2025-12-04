package by.bsuir.warehouseservice.controller;

import by.bsuir.warehouseservice.dto.request.CreateCellRequest;
import by.bsuir.warehouseservice.dto.request.CreateFridgeRequest;
import by.bsuir.warehouseservice.dto.request.CreatePalletRequest;
import by.bsuir.warehouseservice.dto.request.CreateRackRequest;
import by.bsuir.warehouseservice.dto.request.CreateShelfRequest;
import by.bsuir.warehouseservice.dto.response.RackResponse;
import by.bsuir.warehouseservice.service.RackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/racks")
@RequiredArgsConstructor
@Tag(name = "Стеллажи и зоны хранения", description = "API для управления стеллажами, полками, ячейками, паллетами и холодильными камерами")
public class RackController {

    private final RackService rackService;

    @Operation(
            summary = "Создать стеллаж",
            description = "Создает новый стеллаж на складе. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Стеллаж успешно создан",
                    content = @Content(schema = @Schema(implementation = RackResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Склад не найден")
    })
    @PostMapping
    public ResponseEntity<RackResponse> createRack(
            @Valid @RequestBody CreateRackRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        RackResponse response = rackService.createRack(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Создать полку",
            description = "Создает новую полку на стеллаже. Доступно для DIRECTOR и ACCOUNTANT"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Полка успешно создана"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Стеллаж не найден")
    })
    @PostMapping("/{rackId}/shelves")
    public ResponseEntity<Map<String, String>> createShelf(
            @Parameter(description = "ID стеллажа", required = true) @PathVariable UUID rackId,
            @Valid @RequestBody CreateShelfRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

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

    @Operation(
            summary = "Создать ячейку",
            description = "Создает новую ячейку хранения на стеллаже. Доступно для DIRECTOR и ACCOUNTANT"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Ячейка успешно создана"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Стеллаж не найден")
    })
    @PostMapping("/{rackId}/cells")
    public ResponseEntity<Map<String, String>> createCell(
            @Parameter(description = "ID стеллажа", required = true) @PathVariable UUID rackId,
            @Valid @RequestBody CreateCellRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

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

    @Operation(
            summary = "Создать холодильную камеру",
            description = "Создает холодильную камеру на стеллаже с указанием температуры хранения. Доступно для DIRECTOR и ACCOUNTANT"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Холодильник успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Стеллаж не найден")
    })
    @PostMapping("/{rackId}/fridges")
    public ResponseEntity<Map<String, String>> createFridge(
            @Parameter(description = "ID стеллажа", required = true) @PathVariable UUID rackId,
            @Valid @RequestBody CreateFridgeRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

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

    @Operation(
            summary = "Создать паллетное место",
            description = "Создает паллетное место на стеллаже. Доступно для DIRECTOR и ACCOUNTANT"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Паллет успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Стеллаж не найден")
    })
    @PostMapping("/{rackId}/pallets")
    public ResponseEntity<Map<String, String>> createPallet(
            @Parameter(description = "ID стеллажа", required = true) @PathVariable UUID rackId,
            @Valid @RequestBody CreatePalletRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

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

    @Operation(
            summary = "Получить стеллажи склада",
            description = "Возвращает список всех стеллажей для указанного склада"
    )
    @ApiResponse(responseCode = "200", description = "Список стеллажей получен")
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<RackResponse>> getRacksByWarehouse(
            @Parameter(description = "ID склада", required = true) @PathVariable UUID warehouseId) {
        List<RackResponse> response = rackService.getRacksByWarehouse(warehouseId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить стеллаж по ID",
            description = "Возвращает информацию о стеллаже по его идентификатору"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Стеллаж найден"),
            @ApiResponse(responseCode = "404", description = "Стеллаж не найден")
    })
    @GetMapping("/{rackId}")
    public ResponseEntity<RackResponse> getRack(
            @Parameter(description = "ID стеллажа", required = true) @PathVariable UUID rackId) {
        RackResponse response = rackService.getRack(rackId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить информацию о ячейке",
            description = "Возвращает детальную информацию о ячейке хранения"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Информация о ячейке получена"),
            @ApiResponse(responseCode = "404", description = "Ячейка не найдена")
    })
    @GetMapping("/cells/{cellId}")
    public ResponseEntity<Object> getCellInfo(
            @Parameter(description = "ID ячейки", required = true) @PathVariable UUID cellId) {
        Object response = rackService.getCellInfo(cellId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить ячейки стеллажа",
            description = "Возвращает список всех ячеек для указанного стеллажа"
    )
    @ApiResponse(responseCode = "200", description = "Список ячеек получен")
    @GetMapping("/{rackId}/cells")
    public ResponseEntity<List<Object>> getCellsByRack(
            @Parameter(description = "ID стеллажа", required = true) @PathVariable UUID rackId) {
        List<Object> response = rackService.getCellsByRack(rackId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Удалить стеллаж",
            description = "Удаляет стеллаж и все связанные ячейки. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Стеллаж удален"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Стеллаж не найден")
    })
    @DeleteMapping("/{rackId}")
    public ResponseEntity<Map<String, String>> deleteRack(
            @Parameter(description = "ID стеллажа", required = true) @PathVariable UUID rackId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        rackService.deleteRack(rackId);
        return ResponseEntity.ok(Map.of("message", "Стеллаж успешно удалён"));
    }
}
