package by.bsuir.productservice.model.entity;

import by.bsuir.productservice.model.enums.AllocationStrategy;
import by.bsuir.productservice.model.enums.ShipmentRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_request")
@Filter(name = "orgFilter", condition = "organization_id = :orgId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentRequest {

    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "recipient_address", length = 512)
    private String recipientAddress;

    @Column(name = "recipient_inn", length = 50)
    private String recipientInn;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShipmentRequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", length = 16)
    private AllocationStrategy strategy;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (requestId == null) requestId = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (status == null) status = ShipmentRequestStatus.PLANNED;
        if (strategy == null) strategy = AllocationStrategy.AUTO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
