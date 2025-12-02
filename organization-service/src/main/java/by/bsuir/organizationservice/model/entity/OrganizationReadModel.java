package by.bsuir.organizationservice.model.entity;

import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organization_read_model")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationReadModel {

    @Id
    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "unp", nullable = false, unique = true, length = 20)
    private String unp;

    @Column(name = "address", length = 512)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "org_status")
    @org.hibernate.annotations.ColumnTransformer(
            read = "status::text",
            write = "?::org_status"
    )
    private OrganizationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
