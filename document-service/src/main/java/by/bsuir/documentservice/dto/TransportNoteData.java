package by.bsuir.documentservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransportNoteData {

    private String layout;
    private String documentNumber;
    private LocalDate documentDate;
    private String currency;
    private String shipperName;
    private String shipperInn;
    private String shipperAddress;
    private String consigneeName;
    private String consigneeInn;
    private String consigneeAddress;
    private String warehouseName;
    private String contractNumber;
    private String contractDate;
    private String waybillReference;
    private List<TransportItem> items;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private BigDecimal totalVat;
    private String releasedBy;
    private String acceptedBy;
    private String notes;

    @Data
    @Builder
    public static class TransportItem {

        private Integer rowNumber;
        private String productName;
        private String sku;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private BigDecimal vatRate;
        private BigDecimal vatAmount;
    }
}
