package by.bsuir.productservice.model.entity;

import by.bsuir.productservice.model.enums.ReceiptSessionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "receipt_session")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptSession {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "supply_id")
    private UUID supplyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReceiptSessionStatus status;

    @Column(name = "receipt_order_doc_id")
    private UUID receiptOrderDocId;

    @Column(name = "receipt_act_doc_id")
    private UUID receiptActDocId;

    @Column(name = "placement_list_doc_id")
    private UUID placementListDocId;

    @Column(name = "document_error", columnDefinition = "TEXT")
    private String documentError;

    @Column(name = "general_notes", columnDefinition = "TEXT")
    private String generalNotes;

    @Column(name = "contract_number", length = 100)
    private String contractNumber;

    @Column(name = "contract_date")
    private java.time.LocalDate contractDate;

    @Column(name = "responsible_user_id")
    private UUID responsibleUserId;

    @Column(name = "commission_members", columnDefinition = "TEXT")
    private String commissionMembers;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (sessionId == null) {
            sessionId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ReceiptSessionStatus.PAUSED;
        }
    }
}
