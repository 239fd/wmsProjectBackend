package by.bsuir.warehouseservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pallet_place")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PalletPlace {

    @Id
    @Column(name = "place_id")
    private UUID placeId;

    @Column(name = "rack_id", nullable = false)
    private UUID rackId;

    @Column(name = "length_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal heightCm;

    @PrePersist
    protected void onCreate() {
        if (placeId == null) {
            placeId = UUID.randomUUID();
        }
    }
}
