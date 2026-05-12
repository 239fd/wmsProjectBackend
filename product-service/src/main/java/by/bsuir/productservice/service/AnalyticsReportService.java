package by.bsuir.productservice.service;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsReportService {

    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 18f;
    PDType1Font font = PDType1Font.HELVETICA;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ProductAnalyticsService analyticsService;
    private final AbcAnalysisService abcAnalysisService;

    public byte[] generateReport(String preset) {
        LocalDate[] range = presetToRange(preset);
        LocalDate from = range[0];
        LocalDate to = range[1];

        Map<String, Object> dynamics = analyticsService.getOperationsDynamics(from, to);
        Map<String, Object> inventory = analyticsService.getInventoryAnalytics();
        Map<String, Object> abcReport = Map.of(
                "abcItems", abcAnalysisService.getAbcReport().size()
        );

        return buildPdf(preset, from, to, dynamics, inventory);
    }

    private byte[] buildPdf(String preset, LocalDate from, LocalDate to,
                              Map<String, Object> dynamics, Map<String, Object> inventory) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType1Font fontBold = new PDType1Font(font.getCOSObject());
            PDType1Font fontRegular = new PDType1Font(font.getCOSObject());

            float startY = page.getMediaBox().getHeight() - MARGIN;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = startY;

                y = writeLine(cs, fontBold, 16, MARGIN, y,
                        "АНАЛИТИЧЕСКИЙ ОТЧЁТ ПО СКЛАДУ");
                y -= LINE_HEIGHT / 2;
                y = writeLine(cs, fontRegular, 11, MARGIN, y,
                        "Период: " + presetLabel(preset) + "  (" +
                        from.format(DATE_FMT) + " — " + to.format(DATE_FMT) + ")");
                y = writeLine(cs, fontRegular, 11, MARGIN, y,
                        "Сформирован: " + LocalDate.now().format(DATE_FMT));
                y -= LINE_HEIGHT;

                y = writeLine(cs, fontBold, 13, MARGIN, y, "1. Остатки на складе");
                y = writeLine(cs, fontRegular, 11, MARGIN, y,
                        "Уникальных товаров:  " + inventory.getOrDefault("uniqueProducts", "—"));
                y = writeLine(cs, fontRegular, 11, MARGIN, y,
                        "Всего единиц (факт): " + inventory.getOrDefault("totalQuantity", "—"));
                y = writeLine(cs, fontRegular, 11, MARGIN, y,
                        "Зарезервировано:     " + inventory.getOrDefault("reservedQuantity", "—"));
                y = writeLine(cs, fontRegular, 11, MARGIN, y,
                        "Доступно:            " + inventory.getOrDefault("availableQuantity", "—"));
                y -= LINE_HEIGHT;

                y = writeLine(cs, fontBold, 13, MARGIN, y, "2. Операции за период");
                y = writeLine(cs, fontRegular, 11, MARGIN, y,
                        "Всего операций: " + dynamics.getOrDefault("totalOperations", "—"));

                Object byType = dynamics.get("operationsByType");
                if (byType instanceof Map<?, ?> typeMap) {
                    for (Map.Entry<?, ?> e : typeMap.entrySet()) {
                        y = writeLine(cs, fontRegular, 11, MARGIN + 15, y,
                                "• " + e.getKey() + ": " + e.getValue());
                    }
                }
                y -= LINE_HEIGHT;

                y = writeLine(cs, fontBold, 13, MARGIN, y, "3. ABC-анализ");
                y = writeLine(cs, fontRegular, 11, MARGIN, y,
                        "A (высокий оборот, 80% выручки) — ключевые товары");
                y = writeLine(cs, fontRegular, 11, MARGIN, y,
                        "B (средний оборот, 15%) — вспомогательные");
                y = writeLine(cs, fontRegular, 11, MARGIN, y,
                        "C (низкий оборот, 5%) — редкие позиции");
                y = writeLine(cs, fontRegular, 11, MARGIN, y,
                        "Запустите /api/analytics/abc для актуального списка.");
                y -= LINE_HEIGHT * 2;

                cs.setFont(fontRegular, 9);
                cs.beginText();
                cs.newLineAtOffset(MARGIN, y);
                cs.showText("WMS Analytics Report  |  " + LocalDate.now().format(DATE_FMT));
                cs.endText();

                cs.moveTo(MARGIN, y + LINE_HEIGHT);
                cs.lineTo(page.getMediaBox().getWidth() - MARGIN, y + LINE_HEIGHT);
                cs.stroke();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Ошибка генерации аналитического PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать PDF-отчёт: " + e.getMessage(), e);
        }
    }

    private float writeLine(PDPageContentStream cs, PDType1Font font, float size,
                             float x, float y, String text) throws Exception {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(text));
        cs.endText();
        return y - LINE_HEIGHT;
    }

    private String sanitize(String text) {
        return text == null ? "" : text.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String presetLabel(String preset) {
        return switch (preset.toLowerCase(Locale.ROOT)) {
            case "week" -> "Неделя";
            case "month" -> "Месяц";
            case "quarter" -> "Квартал";
            case "year" -> "Год";
            default -> preset;
        };
    }

    private LocalDate[] presetToRange(String preset) {
        LocalDate to = LocalDate.now();
        LocalDate from = switch (preset.toLowerCase(Locale.ROOT)) {
            case "week" -> to.minusWeeks(1);
            case "month" -> to.minusMonths(1);
            case "quarter" -> to.minusMonths(3);
            case "year" -> to.minusYears(1);
            default -> to.minusMonths(1);
        };
        return new LocalDate[]{from, to};
    }
}