package by.bsuir.productservice.model.entity;

import by.bsuir.productservice.model.enums.StorageConditions;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_read_model")
@Filter(name = "orgFilter", condition = "organization_id = :orgId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReadModel {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sku", unique = true, length = 100)
    private String sku;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "unit_of_measure", length = 50)
    private String unitOfMeasure;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name="abc_class", nullable = false)
    private String abcClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_storage_condition", length = 20)
    private StorageConditions requiredStorageCondition;

    @Column(name = "organization_id")
    private UUID organizationId;

    @PrePersist
    protected void onCreate() {
        if (productId == null) {
            productId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (abcClass == null) {
            abcClass = "C";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
