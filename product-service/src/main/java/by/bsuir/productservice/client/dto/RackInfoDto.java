package by.bsuir.productservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RackInfoDto(
        UUID rackId,
        UUID warehouseId,
        String kind,
        String name,
        String storageConditions,
        BigDecimal maxWeightKg,
        Boolean isActive
) {
}
