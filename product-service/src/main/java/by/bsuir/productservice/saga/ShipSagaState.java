package by.bsuir.productservice.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipSagaState {
    private UUID sagaId;
    private UUID productId;
    private UUID warehouseId;
    private UUID organizationId;
    private UUID userId;
    private BigDecimal quantity;
    private UUID reservationId;
    private UUID stagingOperationId;
    private UUID documentId;
    private UUID inventoryId;
    private UUID operationId;
    private String currentStep;
    private String status;
    private String failureReason;
}