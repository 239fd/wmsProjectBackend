package by.bsuir.documentservice.dto;

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
public class ShippingInvoiceData {

    private String invoiceNumber;
    private LocalDate invoiceDate;

    private String shipperName;
    private String shipperAddress;
    private String shipperPhone;
    private String shipperUnp;

    private String consigneeName;
    private String consigneeAddress;
    private String consigneePhone;
    private String consigneeUnp;

    private String carrierName;
    private String carrierVehicle;
    private String driverName;
    private String driverLicense;

    private String loadingPoint;
    private String unloadingPoint;
    private LocalDate shippingDate;

    private List<ShipmentItem> items;

    private Integer totalQuantity;
    private Double totalWeight;
    private Double totalVolume;
    private Double totalCost;

    private String releasedBy;
    private String releasedByPosition;
    private String shippedBy;
    private String shippedByPosition;
    private String receivedBy;
    private String receivedByPosition;

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
