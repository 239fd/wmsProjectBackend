package by.bsuir.productservice.model.entity;

import by.bsuir.productservice.model.enums.AllocationStrategy;
import by.bsuir.productservice.model.enums.DocumentLayout;
import by.bsuir.productservice.model.enums.DomesticDocumentKind;
import by.bsuir.productservice.model.enums.ShipmentRequestStatus;
import by.bsuir.productservice.model.enums.ShipmentType;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_type", nullable = false, length = 16)
    private ShipmentType shipmentType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_layout", nullable = false, length = 16)
    private DocumentLayout documentLayout;

    @Enumerated(EnumType.STRING)
    @Column(name = "domestic_document_kind", nullable = false, length = 8)
    private DomesticDocumentKind domesticDocumentKind;

    @Column(name = "recipient_country", length = 64)
    private String recipientCountry;

    @Column(name = "recipient_gln", length = 32)
    private String recipientGln;

    @Column(name = "picking_list_doc_id")
    private UUID pickingListDocId;

    @Column(name = "document_error", columnDefinition = "TEXT")
    private String documentError;

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
        if (shipmentType == null) shipmentType = ShipmentType.DOMESTIC;
        if (currency == null) currency = "BYN";
        if (documentLayout == null) documentLayout = DocumentLayout.HORIZONTAL;
        if (domesticDocumentKind == null) domesticDocumentKind = DomesticDocumentKind.TN;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
