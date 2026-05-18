package by.bsuir.productservice.service;

import by.bsuir.productservice.client.DocumentClient;
import by.bsuir.productservice.config.GenerationModeContext;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.GeneratedDocument;
import by.bsuir.productservice.repository.GeneratedDocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRegistryService {

    private final DocumentClient documentClient;
    private final DocumentNumberService documentNumberService;
    private final GeneratedDocumentRepository repository;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.presigned-url-ttl-minutes:15}")
    private int presignedUrlTtlMinutes;

    @Transactional
    public GeneratedDocument register(
            UUID operationId,
            String documentType,
            Map<String, Object> payload,
            UUID organizationId,
            UUID userId) {
        return register(operationId, documentType, payload, organizationId, userId,
                GenerationModeContext.current());
    }

    @Transactional
    public GeneratedDocument register(
            UUID operationId,
            String documentType,
            Map<String, Object> payload,
            UUID organizationId,
            UUID userId,
            String mode) {

        if (organizationId == null) {
            throw AppException.badRequest("organizationId обязателен для регистрации документа");
        }
        if (userId == null) {
            throw AppException.badRequest("userId обязателен для регистрации документа");
        }

        Map<String, Object> enrichedPayload = new HashMap<>(payload != null ? payload : Map.of());
        String documentNumber = documentNumberService.next(organizationId, documentType);
        enrichedPayload.putIfAbsent("documentNumber", documentNumber);

        DocumentClient.Fetched fetched = documentClient.fetch(documentType, enrichedPayload, organizationId, mode);
        if (fetched.body() == null || fetched.body().length == 0) {
            throw AppException.internalError(
                    "document-service не вернул контент для типа " + documentType);
        }
        String channel = fetched.channel();
        String fileFormat = channel != null && channel.startsWith("rpa") && !"rpa-fallback-disabled".equals(channel)
                && !"rpa-fallback-unsupported-type".equals(channel)
                && !"rpa-fallback-error".equals(channel)
                ? "xlsx" : "pdf";

        String objectKey = buildObjectKey(organizationId, documentType, documentNumber, fileFormat);
        uploadToMinio(objectKey, fetched.body(), fileFormat);

        GeneratedDocument document = GeneratedDocument.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .operationId(operationId)
                .documentType(documentType)
                .documentNumber(documentNumber)
                .minioObjectKey(objectKey)
                .fileFormat(fileFormat)
                .generatedBy(userId)
                .generatedAt(LocalDateTime.now())
                .payload(serializePayload(enrichedPayload))
                .build();

        log.info("Document {} registered via channel={}, fileFormat={}, key={}",
                documentNumber, channel, fileFormat, objectKey);
        return repository.save(document);
    }

    public byte[] downloadBytes(UUID documentId, UUID organizationId) {
        GeneratedDocument document = repository
                .findByIdAndOrganizationId(documentId, organizationId)
                .orElseThrow(() -> AppException.notFound("Документ не найден: " + documentId));
        try (var stream = minioClient.getObject(io.minio.GetObjectArgs.builder()
                .bucket(bucket)
                .object(document.getMinioObjectKey())
                .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("MinIO: не удалось прочитать {}", document.getMinioObjectKey(), e);
            throw AppException.internalError("Не удалось прочитать файл документа");
        }
    }

    @Transactional
    public void deleteDocument(UUID documentId, UUID organizationId) {
        if (documentId == null) return;
        GeneratedDocument document = repository
                .findByIdAndOrganizationId(documentId, organizationId)
                .orElse(null);
        if (document == null) {
            log.warn("Document {} not found for org {}, skip delete", documentId, organizationId);
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(document.getMinioObjectKey())
                    .build());
            log.info("MinIO: removed {}", document.getMinioObjectKey());
        } catch (Exception e) {
            log.warn("MinIO: failed to remove {} ({}); proceeding with row deletion",
                    document.getMinioObjectKey(), e.getMessage());
        }
        repository.delete(document);
    }

    public String presignedUrl(UUID documentId, UUID organizationId) {
        GeneratedDocument document = repository
                .findByIdAndOrganizationId(documentId, organizationId)
                .orElseThrow(() -> AppException.notFound("Документ не найден: " + documentId));
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(document.getMinioObjectKey())
                    .method(Method.GET)
                    .expiry(presignedUrlTtlMinutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("MinIO: не удалось сгенерировать presigned URL для {}",
                    document.getMinioObjectKey(), e);
            throw AppException.internalError("Не удалось сформировать ссылку на документ");
        }
    }

    private String buildObjectKey(UUID organizationId, String documentType, String documentNumber, String ext) {
        return String.format("%s/%d/%s/%s.%s",
                organizationId, Year.now().getValue(), documentType, documentNumber, ext);
    }

    private void uploadToMinio(String objectKey, byte[] data, String fileFormat) {
        try {
            String contentType = switch (fileFormat) {
                case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "doc" -> "application/msword";
                case "xls" -> "application/vnd.ms-excel";
                case "rtf" -> "application/rtf";
                default -> "application/pdf";
            };
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
            log.info("MinIO: загружен {} ({} bytes, type={})", objectKey, data.length, contentType);
        } catch (Exception e) {
            log.error("MinIO: не удалось загрузить {}", objectKey, e);
            throw AppException.internalError("Не удалось сохранить документ в хранилище");
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Не удалось сериализовать payload: {}", e.getMessage());
            return null;
        }
    }
}
