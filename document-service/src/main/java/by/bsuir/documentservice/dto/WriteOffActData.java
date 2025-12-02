package by.bsuir.documentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO для данных Акта списания
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WriteOffActData {

    // Заголовок
    private String documentNumber;
    private LocalDate documentDate;
    private String organizationName;
    private String inn;
    private String warehouseName;

    // Причина списания
    private String reason;
    private String reasonDescription;
    private String documentBasis;      // Документ-основание

    // Комиссия
    private String chairmanName;
    private List<String> commissionMembers;

    // Материально ответственное лицо
    private String responsiblePerson;

    // Позиции
    private List<WriteOffItem> items;

    // Итоги
    private BigDecimal totalValue;
    private Integer totalQuantity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WriteOffItem {
        private Integer rowNumber;
        private String productName;
        private String sku;
        private String unit;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal value;          // quantity * price
        private String batchNumber;
        private LocalDate expiryDate;
        private String condition;          // Состояние (брак, просрочка и т.д.)
    }
}

