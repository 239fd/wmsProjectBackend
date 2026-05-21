package by.bsuir.productservice.model.entity;

import by.bsuir.productservice.model.enums.SupplyStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "supplies")
@Filter(name = "orgFilter", condition = "organization_id = :orgId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Supply {

    @Id
    @Column(name = "supply_id")
    private UUID supplyId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "supplier_name", length = 255)
    private String supplierName;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "supply_status")
    private SupplyStatus status;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "quantity_only", nullable = false)
    private Boolean quantityOnly;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "actual_date")
    private LocalDate actualDate;

    @Column(name = "total_items", nullable = false)
    private Integer totalItems;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "supply_id", nullable = false, updatable = false)
    @Builder.Default
    private List<SupplyItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (status == null) status = SupplyStatus.PLANNED;
        if (totalItems == null) totalItems = 0;
        if (quantityOnly == null) quantityOnly = Boolean.FALSE;
        if (source == null || source.isBlank()) source = "MANUAL";
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}