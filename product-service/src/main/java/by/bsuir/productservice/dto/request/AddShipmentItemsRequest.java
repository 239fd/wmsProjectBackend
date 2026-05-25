package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddShipmentItemsRequest(
        @NotEmpty(message = "Хотя бы одна позиция обязательна")
        List<CreateShipmentRequestRequest.Item> items
) {
}
