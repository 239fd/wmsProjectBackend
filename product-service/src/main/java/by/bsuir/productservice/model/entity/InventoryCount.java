package by.bsuir.productservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;




@Entity
@Table(name = "inventory_count")
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

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "cell_id")
    private UUID cellId;

    @Column(name = "expected_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal expectedQuantity;

    @Column(name = "actual_quantity", precision = 12, scale = 3)
    private BigDecimal actualQuantity;

    @Column(name = "discrepancy", precision = 12, scale = 3)
    private BigDecimal discrepancy;

    @Column(name = "notes")
    private String notes;
}
