package by.bsuir.warehouseservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fridge")
@Filter(name = "orgFilter", condition = "organization_id = :orgId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fridge {

    @Id
    @Column(name = "rack_id")
    private UUID rackId;


    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "min_temperature_c", precision = 5, scale = 2)
    private BigDecimal minTemperatureC;

    @Column(name = "max_temperature_c", precision = 5, scale = 2)
    private BigDecimal maxTemperatureC;

    @Column(name = "length_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", nullable = false, precision = 8, scale = 2)
    private BigDecimal heightCm;
}
