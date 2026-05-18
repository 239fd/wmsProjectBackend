package by.bsuir.productservice.model.entity;

import by.bsuir.productservice.config.encryption.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "erp_connection")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErpConnection {

    @Id
    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "aggregator", nullable = false, length = 32)
    private String aggregator;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "username", length = 255)
    private String username;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "password_enc", columnDefinition = "TEXT")
    private String password;

    @Column(name = "base_path", length = 512)
    private String basePath;

    @Column(name = "section_name", length = 255)
    private String sectionName;

    @Column(name = "journal_name", length = 255)
    private String journalName;

    @Column(name = "driver_url", length = 255)
    private String driverUrl;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (connectionId == null) {
            connectionId = UUID.randomUUID();
        }
        if (isDefault == null) {
            isDefault = false;
        }
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
