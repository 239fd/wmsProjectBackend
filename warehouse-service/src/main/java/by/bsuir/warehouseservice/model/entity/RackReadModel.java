package by.bsuir.warehouseservice.model.entity;

import by.bsuir.warehouseservice.model.enums.RackKind;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rack_read_model")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RackReadModel {

    @Id
    @Column(name = "rack_id")
    private UUID rackId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, columnDefinition = "rack_kind")
    @org.hibernate.annotations.ColumnTransformer(
            read = "kind::text",
            write = "?::rack_kind"
    )
    private RackKind kind;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

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
