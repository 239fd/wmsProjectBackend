package by.bsuir.productservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CellInfoDto(
        UUID cellId,
        UUID rackId,
        String slotCode,
        String slotType,
        BigDecimal maxWeightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal maxHeightCm,
        BigDecimal remainingHeightCm,
        String palletType
) {
}
