package by.bsuir.documentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO для данных Инвентаризационной описи
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryListData {

    // Заголовок
    private String documentNumber;
    private LocalDate documentDate;
    private LocalDate inventoryDate;
    private String organizationName;
    private String inn;
    private String warehouseName;

    // Комиссия
    private String chairmanName;
    private List<String> commissionMembers;

    // Материально ответственные лица
    private String responsiblePerson;

    // Позиции
    private List<InventoryItem> items;

    // Итоги
    private BigDecimal totalBookValue;
    private BigDecimal totalActualValue;
    private BigDecimal totalDifference;
    private Integer totalItemsCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryItem {
        private Integer rowNumber;
        private String productName;
        private String sku;
        private String unit;
        private Integer bookQuantity;        // По учёту
        private Integer actualQuantity;      // Фактически
        private Integer difference;          // Разница
        private BigDecimal price;
        private BigDecimal bookValue;        // Стоимость по учёту
        private BigDecimal actualValue;      // Фактическая стоимость
        private BigDecimal differenceValue;  // Разница в стоимости
        private String notes;                // Примечания
    }
}

