package by.bsuir.productservice.controller;

import by.bsuir.productservice.config.tenant.TenantContext;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.GeneratedDocument;
import by.bsuir.productservice.repository.GeneratedDocumentRepository;
import by.bsuir.productservice.service.DocumentRegistryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentRegistryController Tests")
class DocumentRegistryControllerTest {

    @Mock
    private GeneratedDocumentRepository repository;

    @Mock
    private DocumentRegistryService registryService;

    @InjectMocks
    private DocumentRegistryController controller;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    private GeneratedDocument sample(UUID id, UUID orgId) {
        return GeneratedDocument.builder()
                .id(id).organizationId(orgId)
                .documentType("receipt-order").documentNumber("ПО-2026-1")
                .minioObjectKey("k").fileFormat("pdf").build();
    }

    @Test
    @DisplayName("list: 200 OK с фильтром по типу")
    void list_givenType_whenCalled_thenFiltersByType() {
        UUID org = UUID.randomUUID();
        when(repository.findByOrganizationIdAndDocumentType(eq(org), eq("receipt-order"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample(UUID.randomUUID(), org))));

        ResponseEntity<?> response = controller.list(org, "receipt-order", PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(repository).findByOrganizationIdAndDocumentType(eq(org), eq("receipt-order"), any());
    }

    @Test
    @DisplayName("list: без фильтра по типу — все документы организации")
    void list_givenNoType_whenCalled_thenAllForOrg() {
        UUID org = UUID.randomUUID();
        when(repository.findByOrganizationId(eq(org), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.list(org, null, PageRequest.of(0, 20));

        verify(repository).findByOrganizationId(eq(org), any());
    }

    @Test
    @DisplayName("list: page size > 100 ограничивается до 100")
    void list_givenLargePageSize_whenCalled_thenCaps() {
        UUID org = UUID.randomUUID();
        when(repository.findByOrganizationId(eq(org), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.list(org, "", PageRequest.of(0, 500));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByOrganizationId(eq(org), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("list: orgId=null + TenantContext установлен → fallback на TenantContext")
    void list_givenNullHeaderAndTenantContext_whenCalled_thenUsesContext() {
        UUID org = UUID.randomUUID();
        TenantContext.set(org);
        when(repository.findByOrganizationId(eq(org), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.list(null, null, PageRequest.of(0, 20));

        verify(repository).findByOrganizationId(eq(org), any());
    }

    @Test
    @DisplayName("list: orgId не определяется → badRequest")
    void list_givenNoOrgIdAnywhere_whenCalled_thenThrows() {
        assertThatThrownBy(() -> controller.list(null, null, PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("организацию");
    }

    @Test
    @DisplayName("getOne: 200 OK")
    void getOne_givenExisting_whenCalled_thenReturns200() {
        UUID org = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(docId, org))
                .thenReturn(Optional.of(sample(docId, org)));

        ResponseEntity<GeneratedDocument> response = controller.getOne(docId, org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getOne: не найден → notFound")
    void getOne_givenMissing_whenCalled_thenThrows() {
        UUID org = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(repository.findByIdAndOrganizationId(docId, org)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getOne(docId, org))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("download: 200 OK + APPLICATION_PDF + inline content-disposition")
    void download_givenExisting_whenCalled_thenReturnsPdf() {
        UUID org = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(registryService.downloadBytes(docId, org)).thenReturn(new byte[]{1, 2, 3});

        ResponseEntity<byte[]> response = controller.download(docId, org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getContentDisposition().toString()).contains("inline");
        assertThat(response.getBody()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("presignedUrl: 200 OK с {url}")
    void presignedUrl_givenExisting_whenCalled_thenReturnsUrl() {
        UUID org = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(registryService.presignedUrl(docId, org)).thenReturn("http://minio/x");

        ResponseEntity<Map<String, String>> response = controller.presignedUrl(docId, org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("url", "http://minio/x");
    }

    @Test
    @DisplayName("byOperation: возвращает список документов по operationId")
    void byOperation_givenOperationId_whenCalled_thenReturnsList() {
        UUID org = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        when(repository.findByOrganizationIdAndOperationIdOrderByGeneratedAtDesc(org, opId))
                .thenReturn(List.of(sample(UUID.randomUUID(), org)));

        ResponseEntity<List<GeneratedDocument>> response = controller.byOperation(opId, org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }
}
