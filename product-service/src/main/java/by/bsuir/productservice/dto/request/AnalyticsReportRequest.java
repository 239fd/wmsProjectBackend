package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AnalyticsReportRequest(
        @NotNull(message = "Дата начала обязательна") LocalDate from,
        @NotNull(message = "Дата окончания обязательна") LocalDate to,
        List<UUID> warehouseIds,
        Set<String> sections,
        boolean saveToRegistry) {

    public static final String SECTION_SUMMARY = "SUMMARY";
    public static final String SECTION_STRUCTURE = "STRUCTURE";
    public static final String SECTION_ABC = "ABC";
    public static final String SECTION_DETAILED = "DETAILED";

    public boolean hasSection(String code) {
        return sections != null && sections.contains(code);
    }
}
