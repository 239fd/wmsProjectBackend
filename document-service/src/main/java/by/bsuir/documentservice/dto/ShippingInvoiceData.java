package by.bsuir.documentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO для данных Товарно-транспортной накладной (ТТН)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingInvoiceData {

    // Номер и дата
    private String invoiceNumber;
    private LocalDate invoiceDate;

    // Грузоотправитель
    private String shipperName;
    private String shipperAddress;
    private String shipperPhone;
    private String shipperUnp; // УНП отправителя

    // Грузополучатель
    private String consigneeName;
    private String consigneeAddress;
    private String consigneePhone;
    private String consigneeUnp; // УНП получателя

    // Перевозчик
    private String carrierName;
    private String carrierVehicle; // Марка и номер ТС
    private String driverName;
    private String driverLicense;

    // Пункт погрузки и выгрузки
    private String loadingPoint;
    private String unloadingPoint;
    private LocalDate shippingDate;

    // Товары
    private List<ShipmentItem> items;

    // Итоги
    private Integer totalQuantity;
    private Double totalWeight;
    private Double totalVolume;
    private Double totalCost;

    // Ответственные лица
    private String releasedBy; // Отпуск разрешил
    private String releasedByPosition;
    private String shippedBy; // Груз принял
    private String shippedByPosition;
    private String receivedBy; // Груз получил
    private String receivedByPosition;

    // Дополнительная информация
    private String notes;
    private String specialConditions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentItem {
        private Integer position;
        private String productName;
        private String productCode;
        private String unit;
        private Integer quantity;
        private Double weight;
        private Double volume;
        private Double price;
        private Double totalPrice;
        private String packagingType;
        private String notes;
    }
}

