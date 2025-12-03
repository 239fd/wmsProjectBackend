package by.bsuir.productservice.model.entity;

import by.bsuir.productservice.model.enums.InventoryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @Column(name = "inventory_id")
    private UUID inventoryId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "cell_id")
    private UUID cellId;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "reserved_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal reservedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR")
    private InventoryStatus status;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @PrePersist
    protected void onCreate() {
        if (inventoryId == null) {
            inventoryId = UUID.randomUUID();
        }
        if (quantity == null) {
            quantity = BigDecimal.ZERO;
        }
        if (reservedQuantity == null) {
            reservedQuantity = BigDecimal.ZERO;
        }
        if (status == null) {
            status = InventoryStatus.AVAILABLE;
        }
        if (lastUpdated == null) {
            lastUpdated = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
}
