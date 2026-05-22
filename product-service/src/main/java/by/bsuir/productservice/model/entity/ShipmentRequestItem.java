package by.bsuir.productservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "shipment_request_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentRequestItem {

    @Id
    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "inventory_id")
    private UUID inventoryId;

    @Column(name = "cell_id")
    private UUID cellId;

    @Column(name = "expected_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal expectedQty;

    @Column(name = "picked_qty", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal pickedQty = BigDecimal.ZERO;

    @Column(name = "unit_sku", length = 20)
    private String unitSku;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @PrePersist
    protected void onCreate() {
        if (itemId == null) itemId = UUID.randomUUID();
        if (pickedQty == null) pickedQty = BigDecimal.ZERO;
        if (status == null) status = "PENDING";
    }
}
