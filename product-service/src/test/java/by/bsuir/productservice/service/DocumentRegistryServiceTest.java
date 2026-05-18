package by.bsuir.productservice.service;

import by.bsuir.productservice.client.DocumentClient;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.GeneratedDocument;
import by.bsuir.productservice.repository.GeneratedDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentRegistryService Tests")
class DocumentRegistryServiceTest {

    @Mock
    private DocumentClient documentClient;

    @Mock
    private DocumentNumberService documentNumberService;

    @Mock
    private GeneratedDocumentRepository repository;

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private DocumentRegistryService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID orgId;
    private UUID userId;
    private UUID operationId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        operationId = UUID.randomUUID();
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "bucket", "wms-documents");
        ReflectionTestUtils.setField(service, "presignedUrlTtlMinutes", 15);
    }

    @Test
    @DisplayName("register: успешный путь — генерирует номер, заливает в MinIO, сохраняет запись")
    void register_givenValidInput_whenCalled_thenSavesGeneratedDocument() throws Exception {
        when(documentNumberService.next(orgId, "receipt-order")).thenReturn("ПО-2026-00001");
        when(documentClient.fetch(eq("receipt-order"), any(), eq(orgId), eq("auto")))
                .thenReturn(new DocumentClient.Fetched(new byte[]{1, 2, 3}, "programmatic"));
        when(repository.save(any(GeneratedDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneratedDocument document = service.register(
                operationId, "receipt-order", Map.of("foo", "bar"), orgId, userId, "auto");

        assertThat(document.getOrganizationId()).isEqualTo(orgId);
        assertThat(document.getDocumentNumber()).isEqualTo("ПО-2026-00001");
        assertThat(document.getFileFormat()).isEqualTo("pdf");
        assertThat(document.getMinioObjectKey()).contains("ПО-2026-00001.pdf");
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(repository).save(any(GeneratedDocument.class));
    }

    @Test
    @DisplayName("register: channel=rpa-* → fileFormat=xlsx")
    void register_givenRpaChannel_whenCalled_thenFileFormatIsXlsx() throws Exception {
        when(documentNumberService.next(orgId, "waybill")).thenReturn("ТТН-2026-00007");
        when(documentClient.fetch(any(), any(), any(), any()))
                .thenReturn(new DocumentClient.Fetched(new byte[]{1}, "rpa-poi"));
        when(repository.save(any(GeneratedDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneratedDocument document = service.register(
                operationId, "waybill", null, orgId, userId, "rpa");

        assertThat(document.getFileFormat()).isEqualTo("xlsx");
    }

    @Test
    @DisplayName("register: rpa-fallback-disabled → fileFormat=pdf")
    void register_givenRpaFallbackChannel_whenCalled_thenFileFormatIsPdf() throws Exception {
        when(documentNumberService.next(any(), any())).thenReturn("ПО-2026-00001");
        when(documentClient.fetch(any(), any(), any(), any()))
                .thenReturn(new DocumentClient.Fetched(new byte[]{1}, "rpa-fallback-disabled"));
        when(repository.save(any(GeneratedDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        GeneratedDocument document = service.register(
                operationId, "receipt-order", Map.of(), orgId, userId);

        assertThat(document.getFileFormat()).isEqualTo("pdf");
    }

    @Test
    @DisplayName("register: orgId=null → badRequest")
    void register_givenNullOrgId_whenCalled_thenThrows() {
        assertThatThrownBy(() -> service.register(
                operationId, "receipt-order", Map.of(), null, userId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("organizationId");
    }

    @Test
    @DisplayName("register: userId=null → badRequest")
    void register_givenNullUserId_whenCalled_thenThrows() {
        assertThatThrownBy(() -> service.register(
                operationId, "receipt-order", Map.of(), orgId, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("userId");
    }

    @Test
    @DisplayName("register: пустой body от document-service → internalError")
    void register_givenEmptyDocumentBody_whenCalled_thenThrowsInternalError() {
        when(documentNumberService.next(any(), any())).thenReturn("ПО-2026-00001");
        when(documentClient.fetch(any(), any(), any(), any()))
                .thenReturn(new DocumentClient.Fetched(null, "error"));

        assertThatThrownBy(() -> service.register(
                operationId, "receipt-order", Map.of(), orgId, userId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("document-service");
    }

    @Test
    @DisplayName("register: документ-номер из payload не перезаписывается, если уже задан")
    void register_givenExistingDocumentNumber_whenCalled_thenKeepsExisting() throws Exception {
        when(documentNumberService.next(orgId, "invoice")).thenReturn("И-2026-00005");
        when(documentClient.fetch(any(), any(), any(), any()))
                .thenReturn(new DocumentClient.Fetched(new byte[]{1}, "programmatic"));
        when(repository.save(any(GeneratedDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("documentNumber", "preserved-number");

        GeneratedDocument document = service.register(
                operationId, "invoice", payload, orgId, userId, "auto");

        // Service generates a number anyway and uses it for the entity (putIfAbsent only enriches payload)
        assertThat(document.getDocumentNumber()).isEqualTo("И-2026-00005");
        // But payload retains the original value
        assertThat(document.getPayload()).contains("preserved-number");
    }

    @Test
    @DisplayName("downloadBytes: возвращает массив байт из MinIO")
    void downloadBytes_givenExisting_whenCalled_thenReturnsBytes() throws Exception {
        UUID documentId = UUID.randomUUID();
        GeneratedDocument document = GeneratedDocument.builder()
                .id(documentId).organizationId(orgId).minioObjectKey("k").build();
        when(repository.findByIdAndOrganizationId(documentId, orgId))
                .thenReturn(Optional.of(document));
        GetObjectResponse response = new GetObjectResponse(
                Headers.of(), "wms-documents", "us-east-1", "k",
                new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        byte[] bytes = service.downloadBytes(documentId, orgId);

        assertThat(bytes).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("downloadBytes: документ не найден → notFound")
    void downloadBytes_givenMissing_whenCalled_thenThrowsNotFound() {
        UUID documentId = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(documentId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadBytes(documentId, orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("downloadBytes: ошибка MinIO → internalError")
    void downloadBytes_givenMinioThrows_whenCalled_thenThrowsInternalError() throws Exception {
        UUID documentId = UUID.randomUUID();
        GeneratedDocument document = GeneratedDocument.builder()
                .id(documentId).organizationId(orgId).minioObjectKey("k").build();
        when(repository.findByIdAndOrganizationId(documentId, orgId))
                .thenReturn(Optional.of(document));
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO down"));

        assertThatThrownBy(() -> service.downloadBytes(documentId, orgId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("deleteDocument: удаляет объект MinIO и запись из БД")
    void deleteDocument_givenExisting_whenCalled_thenDeletesBoth() throws Exception {
        UUID documentId = UUID.randomUUID();
        GeneratedDocument document = GeneratedDocument.builder()
                .id(documentId).organizationId(orgId).minioObjectKey("k").build();
        when(repository.findByIdAndOrganizationId(documentId, orgId))
                .thenReturn(Optional.of(document));

        service.deleteDocument(documentId, orgId);

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(repository).delete(document);
    }

    @Test
    @DisplayName("deleteDocument: документ не найден → no-op")
    void deleteDocument_givenMissing_whenCalled_thenSkips() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(documentId, orgId))
                .thenReturn(Optional.empty());

        service.deleteDocument(documentId, orgId);

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        verify(repository, never()).delete(any(GeneratedDocument.class));
    }

    @Test
    @DisplayName("deleteDocument: null documentId → no-op (идемпотентно)")
    void deleteDocument_givenNullId_whenCalled_thenSkipsLookup() throws Exception {
        service.deleteDocument(null, orgId);

        verify(repository, never()).findByIdAndOrganizationId(any(), any());
        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("deleteDocument: MinIO падает, но row всё равно удаляется")
    void deleteDocument_givenMinioFails_whenCalled_thenStillDeletesRow() throws Exception {
        UUID documentId = UUID.randomUUID();
        GeneratedDocument document = GeneratedDocument.builder()
                .id(documentId).organizationId(orgId).minioObjectKey("k").build();
        when(repository.findByIdAndOrganizationId(documentId, orgId))
                .thenReturn(Optional.of(document));
        org.mockito.Mockito.doThrow(new RuntimeException("MinIO error"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        service.deleteDocument(documentId, orgId);

        verify(repository).delete(document);
    }

    @Test
    @DisplayName("presignedUrl: успешно генерирует URL")
    void presignedUrl_givenExisting_whenCalled_thenReturnsUrl() throws Exception {
        UUID documentId = UUID.randomUUID();
        GeneratedDocument document = GeneratedDocument.builder()
                .id(documentId).organizationId(orgId).minioObjectKey("k").build();
        when(repository.findByIdAndOrganizationId(documentId, orgId))
                .thenReturn(Optional.of(document));
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://minio/x");

        assertThat(service.presignedUrl(documentId, orgId)).isEqualTo("http://minio/x");
    }

    @Test
    @DisplayName("presignedUrl: ошибка MinIO → internalError")
    void presignedUrl_givenMinioThrows_whenCalled_thenThrowsInternalError() throws Exception {
        UUID documentId = UUID.randomUUID();
        GeneratedDocument document = GeneratedDocument.builder()
                .id(documentId).organizationId(orgId).minioObjectKey("k").build();
        when(repository.findByIdAndOrganizationId(documentId, orgId))
                .thenReturn(Optional.of(document));
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("MinIO down"));

        assertThatThrownBy(() -> service.presignedUrl(documentId, orgId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("presignedUrl: документ не найден → notFound")
    void presignedUrl_givenMissing_whenCalled_thenThrowsNotFound() {
        UUID documentId = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(documentId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.presignedUrl(documentId, orgId))
                .isInstanceOf(AppException.class);
    }
}
