package by.bsuir.productservice.model.entity;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "supply_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplyItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "supply_id", nullable = false)
    private UUID supplyId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "expected_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal expectedQty;

    @Column(name = "actual_qty", precision = 12, scale = 3)
    private BigDecimal actualQty;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "notes", length = 255)
    private String notes;
}