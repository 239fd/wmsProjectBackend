package by.bsuir.productservice.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CellLoadResponse(
        UUID cellId,
        int itemsCount,
        BigDecimal totalQuantity,
        boolean occupied
) {
}
