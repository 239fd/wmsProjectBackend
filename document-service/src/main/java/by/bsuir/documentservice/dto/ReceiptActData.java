package by.bsuir.documentservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReceiptActData {

    private String documentNumber;
    private LocalDate documentDate;
    private String organizationName;
    private String inn;
    private String warehouseName;
    private String supplierName;
    private String supplierInn;
    private String contractNumber;
    private String contractDate;
    private String waybillNumber;
    private String waybillDate;
    private String chairmanName;
    private List<String> commissionMembers;
    private String acceptedBy;
    private String approvedBy;
    private String generalNotes;
    private List<DiscrepancyItem> discrepancies;

    public boolean hasDiscrepancies() {
        return discrepancies != null && !discrepancies.isEmpty();
    }

    @Data
    @Builder
    public static class DiscrepancyItem {

        private Integer rowNumber;
        private String productName;
        private String sku;
        private String unit;
        private BigDecimal expectedQty;
        private BigDecimal actualQty;
        private BigDecimal differenceQty;
        private String discrepancyType;
        private String defectDescription;
    }
}
