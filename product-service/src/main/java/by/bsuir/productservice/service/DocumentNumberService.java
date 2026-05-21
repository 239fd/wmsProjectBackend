package by.bsuir.productservice.service;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.DocumentCounter;
import by.bsuir.productservice.repository.DocumentCounterRepository;
import java.time.Year;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentNumberService {

    private static final Map<String, String> PREFIX_BY_TYPE = Map.ofEntries(
            Map.entry("receipt-order", "ПО"),
            Map.entry("receipt-act", "АП"),
            Map.entry("waybill", "ТТН"),
            Map.entry("transport-note", "ТН"),
            Map.entry("cmr", "CMR"),
            Map.entry("inventory-report", "ИНВ"),
            Map.entry("revaluation-act", "ПЕР"),
            Map.entry("write-off-act", "СПС"),
            Map.entry("invoice", "И"),
            Map.entry("picking-list", "ЛП"),
            Map.entry("placement-list", "ЛР"),
            Map.entry("analytics-report", "ОТЧ"));

    private final DocumentCounterRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(UUID organizationId, String documentType) {
        if (organizationId == null) {
            throw AppException.badRequest("organizationId обязателен для генерации номера документа");
        }
        String prefix = PREFIX_BY_TYPE.get(documentType);
        if (prefix == null) {
            throw AppException.badRequest("Неизвестный тип документа: " + documentType);
        }

        int year = Year.now().getValue();
        DocumentCounter counter = repository
                .findForUpdate(organizationId, documentType, year)
                .orElseGet(() -> repository.saveAndFlush(DocumentCounter.builder()
                        .organizationId(organizationId)
                        .documentType(documentType)
                        .year(year)
                        .counter(0L)
                        .build()));

        long next = counter.getCounter() + 1L;
        counter.setCounter(next);
        repository.save(counter);

        return String.format("%s-%d-%05d", prefix, year, next);
    }
}
