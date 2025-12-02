package by.bsuir.warehouseservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "shelf")
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

    @Column(name = "shelf_capacity_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal shelfCapacityKg;

    @Column(name = "length_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal heightCm;

    @PrePersist
    protected void onCreate() {
        if (shelfId == null) {
            shelfId = UUID.randomUUID();
        }
    }
}
