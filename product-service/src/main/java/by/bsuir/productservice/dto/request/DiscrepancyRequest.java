package by.bsuir.productservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DiscrepancyRequest(
        @NotNull(message = "Пользователь обязателен")
        UUID userId,

        String generalNotes,

        @Valid
        List<DiscrepancyItem> items) {

    public record DiscrepancyItem(
            @NotNull(message = "Товар обязателен")
            UUID productId,

            @NotNull(message = "Ожидаемое количество обязательно")
            BigDecimal expectedQty,

            @NotNull(message = "Фактическое количество обязательно")
            BigDecimal actualQty,

            String defectDescription,

            String discrepancyType) {
    }
}
