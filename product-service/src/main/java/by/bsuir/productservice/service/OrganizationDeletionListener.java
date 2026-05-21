package by.bsuir.productservice.service;

import by.bsuir.productservice.config.RabbitMQConfig;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationDeletionListener {

    private static final List<String> ORG_SCOPED_TABLES = List.of(
            "inventory_count",
            "inventory_session",
            "inventory_events",
            "inventory",
            "product_operation_events",
            "product_operation",
            "product_batch",
            "product_events",
            "product_read_model",
            "shipment_request_items",
            "shipment_request",
            "receipt_session",
            "supply_items",
            "supplies",
            "suppliers",
            "extraction_log",
            "saga_state",
            "document_counters",
            "generated_documents"
    );

    private static final List<String> ORG_COLUMN_NAMES = List.of("organization_id", "org_id");

    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String minioBucket;

    @RabbitListener(queues = RabbitMQConfig.ORGANIZATION_DELETED_PRODUCT_QUEUE)
    @Transactional
    public void handleOrganizationDeleted(Map<String, Object> event) {
        try {
            Object orgIdRaw = event.get("orgId");
            if (orgIdRaw == null) {
                log.warn("organization.deleted: некорректное событие: {}", event);
                return;
            }
            UUID orgId = UUID.fromString(orgIdRaw.toString());

            List<String> minioKeys = jdbcTemplate.queryForList(
                    "SELECT minio_object_key FROM generated_documents WHERE organization_id = ?",
                    String.class,
                    orgId
            );

            int totalDeleted = 0;
            for (String table : ORG_SCOPED_TABLES) {
                totalDeleted += deleteByOrg(table, orgId);
            }

            for (String key : minioKeys) {
                if (key == null || key.isBlank()) continue;
                try {
                    minioClient.removeObject(
                            RemoveObjectArgs.builder().bucket(minioBucket).object(key).build());
                } catch (Exception e) {
                    log.warn("Не удалось удалить MinIO объект {}: {}", key, e.getMessage());
                }
            }

            log.info("organization.deleted: очищено {} строк по orgId={} + {} объектов MinIO",
                    totalDeleted, orgId, minioKeys.size());
        } catch (Exception e) {
            log.error("organization.deleted: ошибка обработки: {}", e.getMessage(), e);
        }
    }

    private int deleteByOrg(String table, UUID orgId) {
        for (String column : ORG_COLUMN_NAMES) {
            try {
                int rows = jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " = ?", orgId);
                if (rows > 0) {
                    log.info("organization.deleted: удалено {} строк из {} по {}", rows, table, column);
                }
                return rows;
            } catch (org.springframework.jdbc.BadSqlGrammarException ignored) {
            } catch (org.springframework.dao.InvalidDataAccessResourceUsageException ignored) {
            } catch (Exception e) {
                log.warn("organization.deleted: не удалось удалить из {} по {}: {}", table, column, e.getMessage());
                return 0;
            }
        }
        return 0;
    }
}
