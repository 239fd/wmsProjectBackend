package by.bsuir.documentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO для данных Приходного ордера
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptOrderData {
    // Заголовок документа
    private String documentNumber;
    private LocalDate documentDate;
    private String organizationName;
    private String inn;
    private String warehouseName;
    private String warehouseAddress;

    // Поставщик
    private String supplierName;
    private String supplierInn;
    private String supplierAddress;

    // Ответственные лица
    private String receivedBy;      // Принял
    private String acceptedBy;      // Утвердил
    private String releasedBy;      // Отпустил

    // Позиции товаров
    private List<ReceiptItem> items;

    // Итоги
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


