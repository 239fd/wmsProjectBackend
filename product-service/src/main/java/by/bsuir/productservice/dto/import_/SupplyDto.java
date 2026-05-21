package by.bsuir.productservice.dto.import_;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SupplyDto(
        String externalId,
        SupplierDto supplier,
        UUID warehouseId,
        LocalDate expectedDate,
        String currency,
        BigDecimal totalAmount,
        String notes,
        Boolean quantityOnly,
        Integer totalItems,
        List<SupplyItemDto> items,
        Map<String, Object> snapshot) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SupplierDto(
            String name,
            String fullName,
            String unp,
            String inn,
            String address,
            String phone,
            String email,
            String contactPerson) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SupplyItemDto(
            Integer rowNumber,
            ProductDto product,
            BatchDto batch,
            BigDecimal expectedQty,
            BigDecimal actualQty,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            BigDecimal vatAmount,
            BigDecimal totalAmount,
            String packagingType,
            Boolean markedForWriteoff,
            String notes) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ProductDto(
            String name,
            String sku,
            String barcode,
            String category,
            String description,
            String unitOfMeasure,
            BigDecimal weightKg,
            BigDecimal volumeM3,
            BigDecimal price,
            String manufacturer,
            String storageConditions) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BatchDto(
            String batchNumber,
            LocalDate manufactureDate,
            LocalDate expiryDate,
            BigDecimal purchasePrice,
            String storageConditions) { }
}
