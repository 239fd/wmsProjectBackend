package by.bsuir.documentservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CmrData {

    private String documentNumber;
    private LocalDate documentDate;
    private String currency;

    private String shipperName;
    private String shipperInn;
    private String shipperAddress;
    private String shipperCountry;
    private String shipperGln;

    private String consigneeName;
    private String consigneeInn;
    private String consigneeAddress;
    private String consigneeCountry;
    private String consigneeGln;

    private String carrierName;
    private String carrierAddress;
    private String vehicleNumber;
    private String trailerNumber;
    private String driverName;
    private String driverPassport;

    private String placeOfLoading;
    private String placeOfDelivery;
    private LocalDate loadingDate;
    private LocalDate deliveryDate;

    private List<CmrItem> items;
    private BigDecimal totalQuantity;
    private BigDecimal totalWeight;
    private BigDecimal totalVolume;
    private BigDecimal cargoDeclaredValue;

    private String paymentInstructions;
    private String specialInstructions;
    private String contractNumber;

    private String shipperSignedBy;
    private String carrierSignedBy;
    private String consigneeSignedBy;

    @Data
    @Builder
    public static class CmrItem {

        private Integer rowNumber;
        private String marks;
        private String packagingType;
        private String productName;
        private String hsCode;
        private BigDecimal quantity;
        private String unit;
        private BigDecimal grossWeightKg;
        private BigDecimal volumeM3;
        private BigDecimal declaredValue;
    }
}
