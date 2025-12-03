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
public class WriteOffActData {

    private String documentNumber;
    private LocalDate documentDate;
    private String organizationName;
    private String inn;
    private String warehouseName;

    private String reason;
    private String reasonDescription;
    private String documentBasis;

    private String chairmanName;
    private List<String> commissionMembers;

    private String responsiblePerson;

    private List<WriteOffItem> items;

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
        private BigDecimal value;
        private String batchNumber;
        private LocalDate expiryDate;
        private String condition;
    }
}

