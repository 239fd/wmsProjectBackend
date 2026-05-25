package by.bsuir.warehouseservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "shelf")
@Filter(name = "orgFilter", condition = "organization_id = :orgId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shelf {

    @Id
    @Column(name = "shelf_id")
    private UUID shelfId;

    @Column(name = "rack_id", nullable = false)
    private UUID rackId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "slot_code", nullable = false, length = 32)
    private String slotCode;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "shelf_capacity_kg", precision = 8, scale = 2)
    private BigDecimal shelfCapacityKg;

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
        if (shelfId == null) {
            shelfId = UUID.randomUUID();
        }
        if (remainingHeightCm == null) {
            remainingHeightCm = heightCm;
        }
        if (slotCode == null) {
            slotCode = "SLOT-" + shelfId.toString().substring(0, 8).toUpperCase();
        }
    }
}
