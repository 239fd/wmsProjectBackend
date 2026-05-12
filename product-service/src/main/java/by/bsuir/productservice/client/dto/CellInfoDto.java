package by.bsuir.productservice.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CellInfoDto(
        UUID cellId,
        UUID rackId,
        BigDecimal maxWeightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm
) {
}
