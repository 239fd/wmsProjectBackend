package by.bsuir.documentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO для данных Акта переоценки
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevaluationActData {

    // Заголовок
    private String documentNumber;
    private LocalDate documentDate;
    private String organizationName;
    private String inn;
    private String warehouseName;

    // Причина переоценки
    private String reason;
    private String reasonDescription;

    // Комиссия
    private String chairmanName;
    private List<String> commissionMembers;

    // Позиции
    private List<RevaluationItem> items;

    // Итоги
    private BigDecimal totalOldValue;
    private BigDecimal totalNewValue;
    private BigDecimal totalDifference;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevaluationItem {
        private Integer rowNumber;
        private String productName;
        private String sku;
        private String unit;
        private Integer quantity;
        private BigDecimal oldPrice;
        private BigDecimal newPrice;
        private BigDecimal oldValue;      // quantity * oldPrice
        private BigDecimal newValue;      // quantity * newPrice
        private BigDecimal difference;    // newValue - oldValue
    }
}

