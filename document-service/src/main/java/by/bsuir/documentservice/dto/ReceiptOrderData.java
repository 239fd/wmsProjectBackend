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
public class ReceiptOrderData {
    private String documentNumber;
    private LocalDate documentDate;
    private String organizationName;
    private String inn;
    private String warehouseName;
    private String warehouseAddress;

    private String supplierName;
    private String supplierInn;
    private String supplierAddress;

    private String receivedBy;
    private String acceptedBy;
    private String releasedBy;

    private List<ReceiptItem> items;

    private BigDecimal totalAmount;
    private Integer totalQuantity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptItem {
        private Integer rowNumber;
        private String productName;
        private String sku;
        private String unit;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal amount;
        private String batchNumber;
        private LocalDate expiryDate;
    }
}
