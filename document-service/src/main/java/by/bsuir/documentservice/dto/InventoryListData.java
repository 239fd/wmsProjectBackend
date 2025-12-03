package by.bsuir.documentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryListData {

    private String documentNumber;
    private LocalDate documentDate;
    private LocalDate inventoryDate;
    private String organizationName;
    private String inn;
    private String warehouseName;

    private String chairmanName;
    private List<String> commissionMembers;

    private String responsiblePerson;

    private List<InventoryItem> items;

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
        private Integer bookQuantity;
        private Integer actualQuantity;
        private Integer difference;
        private BigDecimal price;
        private BigDecimal bookValue;
        private BigDecimal actualValue;
        private BigDecimal differenceValue;
        private String notes;
    }
}

