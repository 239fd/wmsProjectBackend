package by.bsuir.productservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "inventory_count")
@Filter(name = "orgFilter", condition = "organization_id = :orgId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCount {

    @Id
    @Column(name = "count_id")
    private UUID countId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "cell_id")
    private UUID cellId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "expected_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal expectedQuantity;

    @Column(name = "actual_quantity", precision = 12, scale = 3)
    private BigDecimal actualQuantity;

    @Column(name = "discrepancy", precision = 12, scale = 3)
    private BigDecimal discrepancy;

    @Column(name = "marked_for_writeoff", nullable = false)
    @Builder.Default
    private Boolean markedForWriteoff = false;

    @Column(name = "notes")
    private String notes;
}
