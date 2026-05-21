package by.bsuir.productservice.service;

import by.bsuir.productservice.client.DocumentClient;
import by.bsuir.productservice.client.WarehouseAnalyticsClient;
import by.bsuir.productservice.dto.request.AnalyticsReportRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.GeneratedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final int ABC_TOP_LIMIT = 50;
    private static final int EXPIRING_WINDOW_DAYS = 30;

    private final ProductAnalyticsService analyticsService;
    private final AbcAnalysisService abcAnalysisService;
    private final WarehouseAnalyticsClient warehouseAnalyticsClient;
    private final DocumentClient documentClient;
    private final DocumentRegistryService documentRegistryService;

    public ReportResult generateReport(
            AnalyticsReportRequest request,
            UUID organizationId,
            UUID userId,
            String userRole) {

        if (request.from() == null || request.to() == null) {
            throw AppException.badRequest("Период (from/to) обязателен");
        }
        if (request.from().isAfter(request.to())) {
            throw AppException.badRequest("Дата начала не может быть позже даты окончания");
        }
        if (request.sections() == null || request.sections().isEmpty()) {
            throw AppException.badRequest("Выберите хотя бы один раздел отчёта");
        }
        if (organizationId == null) {
            throw AppException.badRequest("organizationId обязателен");
        }

        Map<String, Object> payload = buildPayload(request, organizationId, userRole);

        if (request.saveToRegistry()) {
            GeneratedDocument saved = documentRegistryService.register(
                    null, "analytics-report", payload, organizationId, userId);
            byte[] body = documentRegistryService.downloadBytes(saved.getId(), organizationId);
            return new ReportResult(body, saved.getId(), saved.getDocumentNumber());
        }

        DocumentClient.Fetched fetched = documentClient.fetch(
                "analytics-report", payload, organizationId, "auto");
        if (fetched.body() == null || fetched.body().length == 0) {
            throw AppException.internalError("document-service не вернул содержимое отчёта");
        }
        return new ReportResult(fetched.body(), null, null);
    }

    private Map<String, Object> buildPayload(
            AnalyticsReportRequest request, UUID organizationId, String userRole) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("organizationId", organizationId.toString());
        payload.put("periodFrom", request.from().format(DATE_FMT));
        payload.put("periodTo", request.to().format(DATE_FMT));
        payload.put("generatedAt", LocalDateTime.now().format(DATETIME_FMT));

        List<String> sectionsIncluded = new ArrayList<>();

        if (request.hasSection(AnalyticsReportRequest.SECTION_SUMMARY)
                || request.hasSection(AnalyticsReportRequest.SECTION_STRUCTURE)) {
            Map<String, Object> summary = warehouseAnalyticsClient
                    .getOrganizationSummary(organizationId, userRole);

            if (request.hasSection(AnalyticsReportRequest.SECTION_SUMMARY)) {
                payload.put("summary", buildSummarySection(summary));
                sectionsIncluded.add(AnalyticsReportRequest.SECTION_SUMMARY);
            }

            if (request.hasSection(AnalyticsReportRequest.SECTION_STRUCTURE)) {
                payload.put("structure",
                        buildStructureSection(summary, request.warehouseIds()));
                sectionsIncluded.add(AnalyticsReportRequest.SECTION_STRUCTURE);
            }
        }

        if (request.hasSection(AnalyticsReportRequest.SECTION_ABC)) {
            payload.put("abc", buildAbcSection());
            sectionsIncluded.add(AnalyticsReportRequest.SECTION_ABC);
        }

        if (request.hasSection(AnalyticsReportRequest.SECTION_DETAILED)) {
            payload.put("detailed", buildDetailedSection(request.from(), request.to()));
            sectionsIncluded.add(AnalyticsReportRequest.SECTION_DETAILED);
        }

        payload.put("sectionsIncluded", sectionsIncluded);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSummarySection(Map<String, Object> summary) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalWarehouses", summary.getOrDefault("totalWarehouses", 0));
        result.put("activeWarehouses", summary.getOrDefault("activeWarehouses", 0));

        Object whList = summary.get("warehouses");
        long totalSlots = 0;
        long occupiedSlots = 0;
        if (whList instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Object struct = ((Map<String, Object>) m).get("structure");
                if (struct instanceof Map<?, ?> sm) {
                    totalSlots += longOf(sm.get("totalSlots"));
                    occupiedSlots += longOf(sm.get("occupiedSlots"));
                }
            }
        }
        double utilization = totalSlots > 0
                ? Math.round(((double) occupiedSlots / totalSlots) * 1000.0) / 10.0
                : 0.0;
        result.put("totalSlots", totalSlots);
        result.put("occupiedSlots", occupiedSlots);
        result.put("freeSlots", totalSlots - occupiedSlots);
        result.put("utilizationPercent", utilization);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildStructureSection(
            Map<String, Object> summary, List<UUID> warehouseIds) {

        Object whList = summary.get("warehouses");
        if (!(whList instanceof List<?> list)) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Map<String, Object> wh = (Map<String, Object>) m;
            if (warehouseIds != null && !warehouseIds.isEmpty()) {
                Object idRaw = wh.get("warehouseId");
                if (idRaw == null) continue;
                try {
                    if (!warehouseIds.contains(UUID.fromString(idRaw.toString()))) continue;
                } catch (IllegalArgumentException ex) {
                    continue;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("warehouseId", wh.get("warehouseId"));
            row.put("name", wh.get("name"));
            row.put("address", wh.getOrDefault("address", ""));
            row.put("isActive", wh.get("isActive"));
            row.put("structure", normalizeStructure(wh.get("structure")));
            result.add(row);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildAbcSection() {
        Map<String, Object> distribution = analyticsService.getAbcDistribution();
        if (distribution == null) distribution = new LinkedHashMap<>();
        Map<String, Object> productCountByClass = new LinkedHashMap<>();
        productCountByClass.put("A", 0L);
        productCountByClass.put("B", 0L);
        productCountByClass.put("C", 0L);
        Object raw = distribution.get("productCountByClass");
        if (raw instanceof Map<?, ?> rm) {
            for (Map.Entry<?, ?> e : rm.entrySet()) {
                productCountByClass.put(String.valueOf(e.getKey()), longOf(e.getValue()));
            }
        }
        distribution.put("productCountByClass", productCountByClass);

        List<Map<String, Object>> fullReport = abcAnalysisService.getAbcReport();
        List<Map<String, Object>> top = fullReport.stream()
                .limit(ABC_TOP_LIMIT)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("distribution", distribution);
        result.put("topProducts", top);
        result.put("totalProducts", fullReport.size());
        return result;
    }

    private Map<String, Object> normalizeStructure(Object raw) {
        Map<String, Object> s = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> sm) {
            @SuppressWarnings("unchecked")
            Map<String, Object> src = (Map<String, Object>) sm;
            s.putAll(src);
        }
        s.putIfAbsent("racksCount", 0);
        s.putIfAbsent("totalSlots", 0);
        s.putIfAbsent("occupiedSlots", 0);
        s.putIfAbsent("freeSlots", 0);
        s.putIfAbsent("utilizationPercent", 0);
        Map<String, Object> kinds = new LinkedHashMap<>();
        Object existingKinds = s.get("racksByKind");
        if (existingKinds instanceof Map<?, ?> km) {
            for (Map.Entry<?, ?> e : km.entrySet()) {
                kinds.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        kinds.putIfAbsent("SHELF", 0);
        kinds.putIfAbsent("CELL", 0);
        kinds.putIfAbsent("PALLET", 0);
        s.put("racksByKind", kinds);
        return s;
    }

    private Map<String, Object> buildDetailedSection(LocalDate from, LocalDate to) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> inventory = analyticsService.getInventoryAnalytics();
        result.put("inventory", inventory);

        Map<String, Object> dynamics = analyticsService.getOperationsDynamics(from, to);
        result.put("operations", dynamics);

        List<Map<String, Object>> expiring = analyticsService.getExpiringProducts(EXPIRING_WINDOW_DAYS);
        result.put("expiring", expiring);
        result.put("expiringCount", expiring.size());

        Map<String, Object> comparison = analyticsService.getInventoryComparison(from, to);
        result.put("inventoryComparison", comparison);
        return result;
    }

    private long longOf(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public record ReportResult(byte[] body, UUID documentId, String documentNumber) { }
}
