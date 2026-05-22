package by.bsuir.productservice.model.entity;

import by.bsuir.productservice.model.enums.PackagingType;
import by.bsuir.productservice.model.enums.StorageConditions;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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

    @Column(name = "supply_id", nullable = false, insertable = false, updatable = false)
    private UUID supplyId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "row_number")
    private Integer rowNumber;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "sku", length = 100)
    private String sku;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "unit_of_measure", length = 50)
    private String unitOfMeasure;

    @Column(name = "manufacturer", length = 255)
    private String manufacturer;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_conditions", length = 20)
    private StorageConditions storageConditions;

    @Column(name = "expected_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal expectedQty;

    @Column(name = "actual_qty", precision = 12, scale = 3)
    private BigDecimal actualQty;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "vat_rate", precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "vat_amount", precision = 12, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

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

    @Column(name = "package_weight_kg", precision = 10, scale = 3)
    private BigDecimal packageWeightKg;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "purchase_price", precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "marked_for_writeoff", nullable = false)
    private Boolean markedForWriteoff;

    @Column(name = "notes", length = 255)
    private String notes;

    @PrePersist
    void onCreate() {
        if (markedForWriteoff == null) markedForWriteoff = Boolean.FALSE;
    }
}
