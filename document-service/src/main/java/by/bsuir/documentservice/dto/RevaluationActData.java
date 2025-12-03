package by.bsuir.documentservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevaluationActData {

    private String documentNumber;
    private LocalDate documentDate;
    private String organizationName;
    private String inn;
    private String warehouseName;

    private String reason;
    private String reasonDescription;

    private String chairmanName;
    private List<String> commissionMembers;

    private List<RevaluationItem> items;

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
        private BigDecimal oldValue;
        private BigDecimal newValue;
        private BigDecimal difference;
    }
}
