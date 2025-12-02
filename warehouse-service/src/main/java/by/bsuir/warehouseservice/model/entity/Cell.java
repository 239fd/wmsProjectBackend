package by.bsuir.warehouseservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cell")
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

    @Column(name = "max_weight_kg", precision = 8, scale = 2)
    private BigDecimal maxWeightKg;

    @Column(name = "length_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal heightCm;

    @PrePersist
    protected void onCreate() {
        if (cellId == null) {
            cellId = UUID.randomUUID();
        }
    }
}
