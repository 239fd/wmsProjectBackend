package by.bsuir.productservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_session")
@Filter(name = "orgFilter", condition = "organization_id = :orgId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySession {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "started_by", nullable = false)
    private UUID startedBy;

    @Column(name = "responsible_user_id")
    private UUID responsibleUserId;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "commission_members", columnDefinition = "jsonb")
    private String commissionMembers;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Column(name = "notes")
    private String notes;

    public enum SessionStatus {
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }
}
