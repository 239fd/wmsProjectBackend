package by.bsuir.productservice.dto.request;

import by.bsuir.productservice.model.enums.AllocationStrategy;
import by.bsuir.productservice.model.enums.DocumentLayout;
import by.bsuir.productservice.model.enums.DomesticDocumentKind;
import by.bsuir.productservice.model.enums.ShipmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateShipmentRequestRequest(
        @NotNull(message = "Warehouse ID обязателен")
        UUID warehouseId,

        @NotBlank(message = "Получатель обязателен")
        String recipientName,

        @NotBlank(message = "Адрес получателя обязателен")
        String recipientAddress,

        @NotBlank(message = "УНП/ИНН получателя обязателен")
        @Pattern(regexp = "^\\d{9}$|^\\d{10}$|^\\d{12}$",
                message = "УНП — 9 цифр (РБ) или ИНН — 10/12 цифр (РФ)")
        String recipientInn,

        LocalDate plannedDate,
        String comment,

        AllocationStrategy strategy,

        ShipmentType shipmentType,
        String currency,
        DocumentLayout documentLayout,
        DomesticDocumentKind domesticDocumentKind,
        String recipientCountry,
        String recipientGln,

        @NotEmpty(message = "Хотя бы одна позиция обязательна")
        List<Item> items
) {
    public record Item(
            @NotNull(message = "Product ID обязателен")
            UUID productId,

            UUID batchId,

            @NotNull(message = "Количество обязательно")
            @Positive(message = "Количество должно быть положительным")
            BigDecimal expectedQty
    ) {
    }
}
