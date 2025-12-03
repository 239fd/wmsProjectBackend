package by.bsuir.organizationservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organization_invitation_codes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationInvitationCode {

    @Id
    @Column(name = "code_id")
    private UUID codeId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "invitation_code", nullable = false, unique = true, length = 64)
    private String invitationCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @PrePersist
    protected void onCreate() {
        if (codeId == null) {
            codeId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
