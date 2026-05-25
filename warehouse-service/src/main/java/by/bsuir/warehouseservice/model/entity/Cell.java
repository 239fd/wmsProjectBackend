package by.bsuir.warehouseservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cell")
@Filter(name = "orgFilter", condition = "organization_id = :orgId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cell {

    @Id
    @Column(name = "cell_id")
    private UUID cellId;

    @Column(name = "rack_id", nullable = false)
    private UUID rackId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "slot_code", nullable = false, length = 32)
    private String slotCode;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "max_weight_kg", precision = 8, scale = 2)
    private BigDecimal maxWeightKg;

    @Column(name = "length_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "remaining_height_cm", precision = 8, scale = 2)
    private BigDecimal remainingHeightCm;

    @PrePersist
    protected void onCreate() {
        if (cellId == null) {
            cellId = UUID.randomUUID();
        }
        if (remainingHeightCm == null) {
            remainingHeightCm = heightCm;
        }
        if (slotCode == null) {
            slotCode = "SLOT-" + cellId.toString().substring(0, 8).toUpperCase();
        }
    }
}
