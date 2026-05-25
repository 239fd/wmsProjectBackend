package by.bsuir.productservice.service;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.DocumentCounter;
import by.bsuir.productservice.repository.DocumentCounterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentNumberService Tests")
class DocumentNumberServiceTest {

    @Mock
    private DocumentCounterRepository repository;

    @InjectMocks
    private DocumentNumberService service;

    private final UUID orgId = UUID.randomUUID();
    private final int currentYear = Year.now().getValue();

    @Test
    @DisplayName("next: первый вызов для (org, type, year) создаёт счётчик и возвращает -00001")
    void next_givenNoCounter_whenCalled_thenCreatesCounterAndReturnsFirst() {
        when(repository.findForUpdate(eq(orgId), eq("receipt-order"), eq(currentYear)))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(DocumentCounter.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(repository.save(any(DocumentCounter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String number = service.next(orgId, "receipt-order");

        assertThat(number).isEqualTo(String.format("ПО-%d-00001", currentYear));
        ArgumentCaptor<DocumentCounter> captor = ArgumentCaptor.forClass(DocumentCounter.class);
        verify(repository).saveAndFlush(captor.capture());
        DocumentCounter saved = captor.getValue();
        assertThat(saved.getOrganizationId()).isEqualTo(orgId);
        assertThat(saved.getDocumentType()).isEqualTo("receipt-order");
        assertThat(saved.getYear()).isEqualTo(currentYear);
        assertThat(saved.getCounter()).isEqualTo(1L);
    }

    @Test
    @DisplayName("next: существующий счётчик инкрементируется и сохраняется")
    void next_givenExistingCounter_whenCalled_thenIncrementsAndSaves() {
        DocumentCounter existing = DocumentCounter.builder()
                .organizationId(orgId)
                .documentType("waybill")
                .year(currentYear)
                .counter(41L)
                .build();
        when(repository.findForUpdate(eq(orgId), eq("waybill"), eq(currentYear)))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(DocumentCounter.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String number = service.next(orgId, "waybill");

        assertThat(number).isEqualTo(String.format("ТТН-%d-00042", currentYear));
        assertThat(existing.getCounter()).isEqualTo(42L);
        verify(repository, times(1)).save(existing);
    }

    @Test
    @DisplayName("next: org=null → AppException badRequest")
    void next_givenNullOrgId_whenCalled_thenThrowsAppException() {
        assertThatThrownBy(() -> service.next(null, "receipt-order"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("organizationId");
    }

    @Test
    @DisplayName("next: неизвестный type → AppException badRequest")
    void next_givenUnknownType_whenCalled_thenThrowsAppException() {
        assertThatThrownBy(() -> service.next(orgId, "totally-unknown-type"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Неизвестный тип документа");
    }

    @Test
    @DisplayName("next: все 11 типов имеют корректный префикс")
    void next_givenAllSupportedTypes_whenCalled_thenReturnsExpectedPrefix() {
        when(repository.findForUpdate(eq(orgId), anyString(), anyInt()))
                .thenAnswer(inv -> Optional.of(DocumentCounter.builder()
                        .organizationId(orgId)
                        .documentType(inv.getArgument(1))
                        .year(inv.getArgument(2))
                        .counter(0L)
                        .build()));
        when(repository.save(any(DocumentCounter.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.next(orgId, "receipt-order")).startsWith("ПО-");
        assertThat(service.next(orgId, "receipt-act")).startsWith("АП-");
        assertThat(service.next(orgId, "waybill")).startsWith("ТТН-");
        assertThat(service.next(orgId, "transport-note")).startsWith("ТН-");
        assertThat(service.next(orgId, "cmr")).startsWith("CMR-");
        assertThat(service.next(orgId, "inventory-report")).startsWith("ИНВ-");
        assertThat(service.next(orgId, "revaluation-act")).startsWith("ПЕР-");
        assertThat(service.next(orgId, "write-off-act")).startsWith("СПС-");
        assertThat(service.next(orgId, "invoice")).startsWith("И-");
        assertThat(service.next(orgId, "picking-list")).startsWith("ЛП-");
        assertThat(service.next(orgId, "placement-list")).startsWith("ЛР-");
    }
}
