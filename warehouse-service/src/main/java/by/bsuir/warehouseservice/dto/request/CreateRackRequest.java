package by.bsuir.warehouseservice.dto.request;

import by.bsuir.warehouseservice.model.enums.RackKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateRackRequest(
        @NotNull(message = "ID склада обязателен")
        UUID warehouseId,

        @NotNull(message = "Тип стеллажа обязателен")
        RackKind kind,

        @NotBlank(message = "Название стеллажа обязательно")
        @Size(max = 255, message = "Название не должно превышать 255 символов")
        String name
) {
}
