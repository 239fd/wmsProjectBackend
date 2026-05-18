package by.bsuir.productservice.dto.request;

import java.util.UUID;

public record RunErpExtractionRequest(
        UUID connectionId,
        String aggregator,
        String username,
        String password,
        String basePath,
        String sectionName,
        String journalName,
        String driverUrl
) {
}
