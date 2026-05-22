package by.bsuir.productservice.dto.request;

import by.bsuir.productservice.model.enums.PackagingType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Запрос на создание плановой поставки")
public record CreateSupplyRequest(
        @JsonProperty("supplierId") UUID supplierId,
        @JsonProperty("supplierName") String supplierName,
        @JsonProperty("warehouseId") UUID warehouseId,
        @JsonProperty("expectedDate") LocalDate expectedDate,
        @JsonProperty("currency") String currency,
        @JsonProperty("totalAmount") BigDecimal totalAmount,
        @JsonProperty("notes") String notes,
        @JsonProperty("createdBy") UUID createdBy,
        @JsonProperty("quantityOnly") Boolean quantityOnly,
        @JsonProperty("totalItems") Integer totalItems,
        @JsonProperty("items") @Valid List<SupplyItemRequest> items
) {

    public record SupplyItemRequest(
            @JsonProperty("productId") UUID productId,
            @JsonProperty("productName") String productName,
            @JsonProperty("sku") String sku,
            @JsonProperty("barcode") String barcode,
            @JsonProperty("category") String category,
            @JsonProperty("unitOfMeasure") String unitOfMeasure,
            @JsonProperty("manufacturer") String manufacturer,
            @JsonProperty("storageConditions") String storageConditions,
            @JsonProperty("expectedQty") BigDecimal expectedQty,
            @JsonProperty("unitPrice") BigDecimal unitPrice,
            @JsonProperty("vatRate") BigDecimal vatRate,
            @JsonProperty("vatAmount") BigDecimal vatAmount,
            @JsonProperty("totalAmount") BigDecimal totalAmount,
            @JsonProperty("packagingType") PackagingType packagingType,
            @JsonProperty("unitsPerPackage") Integer unitsPerPackage,
            @JsonProperty("packageLengthCm") BigDecimal packageLengthCm,
            @JsonProperty("packageWidthCm") BigDecimal packageWidthCm,
            @JsonProperty("packageHeightCm") BigDecimal packageHeightCm,
            @JsonProperty("packageWeightKg") BigDecimal packageWeightKg,
            @JsonProperty("batchNumber") String batchNumber,
            @JsonProperty("manufactureDate") LocalDate manufactureDate,
            @JsonProperty("expiryDate") LocalDate expiryDate,
            @JsonProperty("purchasePrice") BigDecimal purchasePrice,
            @JsonProperty("markedForWriteoff") Boolean markedForWriteoff,
            @JsonProperty("notes") String notes
    ) {
    }
}
