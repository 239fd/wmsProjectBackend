package by.bsuir.warehouseservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWarehouseRequest(
        @NotNull(message = "ID организации обязателен")
        UUID orgId,

        @NotBlank(message = "Название склада обязательно")
        @Size(max = 255, message = "Название не должно превышать 255 символов")
        String name,

        @Size(max = 512, message = "Адрес не должен превышать 512 символов")
        String address,

        UUID responsibleUserId
) {
}
