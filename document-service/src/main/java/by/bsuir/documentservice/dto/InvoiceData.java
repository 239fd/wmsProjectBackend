package by.bsuir.documentservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvoiceData {

    private String documentNumber;
    private LocalDate documentDate;
    private String currency;
    private String sellerName;
    private String sellerInn;
    private String sellerAddress;
    private String buyerName;
    private String buyerInn;
    private String buyerAddress;
    private String contractNumber;
    private String contractDate;
    private List<InvoiceItem> items;
    private BigDecimal totalAmount;
    private String totalAmountInWords;
    private BigDecimal vatRate;
    private BigDecimal vatAmount;
    private String responsiblePerson;
    private String notes;

    @Data
    @Builder
    public static class InvoiceItem {

        private Integer rowNumber;
        private String productName;
        private String sku;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
    }
}
