package by.bsuir.warehouseservice.dto.request;

import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateWarehouseRequest(
        @Size(max = 255, message = "Название не должно превышать 255 символов")
        String name,

        @Size(max = 512, message = "Адрес не должен превышать 512 символов")
        String address,

        UUID responsibleUserId,

        Boolean isActive
) {
}
