package by.bsuir.productservice.model.entity;

import by.bsuir.productservice.model.enums.PackagingType;
import by.bsuir.productservice.model.enums.StorageConditions;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_batch")
@Filter(name = "orgFilter", condition = "organization_id = :orgId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBatch {

    @Id
    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "supply_id")
    private UUID supplyId;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "supplier")
    private String supplier;

    @Column(name = "purchase_price", precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_conditions", length = 20)
    private StorageConditions storageConditions;

    @Enumerated(EnumType.STRING)
    @Column(name = "packaging_type", length = 10)
    private PackagingType packagingType;

    @Column(name = "units_per_package")
    private Integer unitsPerPackage;

    @Column(name = "package_length_cm", precision = 8, scale = 2)
    private BigDecimal packageLengthCm;

    @Column(name = "package_width_cm", precision = 8, scale = 2)
    private BigDecimal packageWidthCm;

    @Column(name = "package_height_cm", precision = 8, scale = 2)
    private BigDecimal packageHeightCm;

    @Column(name = "package_weight_kg", precision = 14, scale = 3)
    private BigDecimal packageWeightKg;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (batchId == null) {
            batchId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
