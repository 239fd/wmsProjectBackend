package by.bsuir.productservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "planned_deliveries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannedDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "external_id", nullable = false, unique = true, length = 100)
    private String externalId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "supplier_name", length = 255)
    private String supplierName;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "expected_quantity")
    private Integer expectedQuantity;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "extracted_at", nullable = false)
    private LocalDateTime extractedAt;

    @Column(name = "processed", nullable = false)
    private Boolean processed;

    @PrePersist
    protected void onCreate() {
        if (extractedAt == null) extractedAt = LocalDateTime.now();
        if (processed == null) processed = false;
    }
}