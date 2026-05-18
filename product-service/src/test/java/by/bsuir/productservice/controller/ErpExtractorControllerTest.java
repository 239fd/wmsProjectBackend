package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.RunErpExtractionRequest;
import by.bsuir.productservice.model.entity.ErpConnection;
import by.bsuir.productservice.model.entity.PlannedDelivery;
import by.bsuir.productservice.repository.ExtractionLogRepository;
import by.bsuir.productservice.repository.PlannedDeliveryRepository;
import by.bsuir.productservice.rpa.ErpConnectionParams;
import by.bsuir.productservice.rpa.ErpExtractorJob;
import by.bsuir.productservice.service.ErpConnectionService;
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
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ErpExtractorController Tests")
class ErpExtractorControllerTest {

    @Mock
    private ErpExtractorJob extractorJob;

    @Mock
    private PlannedDeliveryRepository deliveryRepository;

    @Mock
    private ExtractionLogRepository logRepository;

    @Mock
    private ErpConnectionService erpConnectionService;

    @InjectMocks
    private ErpExtractorController controller;

    @Test
    @DisplayName("runExtraction: connectionId в body → берёт креды из БД")
    void runExtraction_givenConnectionIdInBody_whenCalled_thenLoadsFromDb() {
        UUID connectionId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        ErpConnection connection = ErpConnection.builder()
                .connectionId(connectionId).aggregator("onec")
                .username("u").password("p").build();
        RunErpExtractionRequest body = new RunErpExtractionRequest(
                connectionId, null, null, null, null, null, null, null);
        when(erpConnectionService.findById(connectionId, org)).thenReturn(Optional.of(connection));
        when(extractorJob.runManually(eq("onec"), any(ErpConnectionParams.class)))
                .thenReturn(Map.of("status", "ok"));

        ResponseEntity<Map<String, Object>> response = controller.runExtraction("onec", body, org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<ErpConnectionParams> captor = ArgumentCaptor.forClass(ErpConnectionParams.class);
        verify(extractorJob).runManually(eq("onec"), captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("u");
        assertThat(captor.getValue().password()).isEqualTo("p");
    }

    @Test
    @DisplayName("runExtraction: inline-credentials → берёт из body")
    void runExtraction_givenInlineCreds_whenCalled_thenUsesInline() {
        RunErpExtractionRequest body = new RunErpExtractionRequest(
                null, "api", "inlineU", "inlineP", null, null, null, null);
        when(extractorJob.runManually(eq("api"), any(ErpConnectionParams.class)))
                .thenReturn(Map.of());

        controller.runExtraction("api", body, UUID.randomUUID());

        ArgumentCaptor<ErpConnectionParams> captor = ArgumentCaptor.forClass(ErpConnectionParams.class);
        verify(extractorJob).runManually(eq("api"), captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("inlineU");
    }

    @Test
    @DisplayName("runExtraction: всё пусто, orgId есть → fallback на default")
    void runExtraction_givenAllEmptyAndOrgId_whenCalled_thenFallsBackToDefault() {
        UUID org = UUID.randomUUID();
        ErpConnection defaultConn = ErpConnection.builder()
                .connectionId(UUID.randomUUID()).aggregator("onec")
                .username("default-u").password("default-p").build();
        when(erpConnectionService.findDefault(org, "onec")).thenReturn(Optional.of(defaultConn));
        when(extractorJob.runManually(eq("onec"), any(ErpConnectionParams.class)))
                .thenReturn(Map.of());

        controller.runExtraction("onec", null, org);

        ArgumentCaptor<ErpConnectionParams> captor = ArgumentCaptor.forClass(ErpConnectionParams.class);
        verify(extractorJob).runManually(eq("onec"), captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("default-u");
    }

    @Test
    @DisplayName("runExtraction: всё null → null params")
    void runExtraction_givenAllNull_whenCalled_thenPassesNull() {
        when(extractorJob.runManually(eq("onec"), any())).thenReturn(Map.of());

        controller.runExtraction("onec", null, null);

        verify(extractorJob).runManually(eq("onec"), eq(null));
    }

    @Test
    @DisplayName("getPendingDeliveries: возвращает Page<>")
    void getPendingDeliveries_whenCalled_thenReturnsPage() {
        when(deliveryRepository.findByProcessedFalseOrderByExpectedDateAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(PlannedDelivery.builder().externalId("x").build())));

        ResponseEntity<?> response = controller.getPendingDeliveries(PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getPendingDeliveries: size > 100 ограничивается до 100")
    void getPendingDeliveries_givenLargePageSize_whenCalled_thenCaps() {
        when(deliveryRepository.findByProcessedFalseOrderByExpectedDateAsc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.getPendingDeliveries(PageRequest.of(0, 500));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(deliveryRepository).findByProcessedFalseOrderByExpectedDateAsc(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getExtractionLog: возвращает RPA + API логи")
    void getExtractionLog_whenCalled_thenReturnsBothSources() {
        when(logRepository.findTop10BySourceOrderByExtractedAtDesc("RPA")).thenReturn(List.of());
        when(logRepository.findTop10BySourceOrderByExtractedAtDesc("API")).thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response = controller.getExtractionLog();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("rpa", "api");
    }
}
