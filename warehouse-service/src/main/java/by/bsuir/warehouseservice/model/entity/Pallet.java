package by.bsuir.warehouseservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pallet")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pallet {

    @Id
    @Column(name = "rack_id")
    private UUID rackId;

    @Column(name = "pallet_place_count", nullable = false)
    private Integer palletPlaceCount;

    @Column(name = "max_weight_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxWeightKg;
}
