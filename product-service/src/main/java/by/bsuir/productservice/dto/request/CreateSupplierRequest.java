package by.bsuir.productservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на создание поставщика")
public record CreateSupplierRequest(
        @Schema(description = "Наименование поставщика", example = "ОАО Молочные продукты")
        @NotBlank(message = "Наименование поставщика обязательно")
        @Size(max = 255, message = "Наименование не более 255 символов")
        String name,

        @Schema(description = "ИНН поставщика (9 цифр)", example = "100234567")
        @Size(max = 20, message = "ИНН не более 20 символов")
        String unp,

        @Schema(description = "Контактное лицо", example = "Иванов Иван Иванович")
        @Size(max = 255, message = "Контактное лицо не более 255 символов")
        String contactPerson,

        @Schema(description = "Телефон контактного лица", example = "+375291234567")
        @Size(max = 50, message = "Телефон не более 50 символов")
        String phone,

        @Schema(description = "Email контактного лица", example = "supplier@milk.by")
        @Size(max = 255, message = "Email не более 255 символов")
        String email,

        @Schema(description = "Адрес поставщика", example = "г. Минск, ул. Производственная, 10")
        @Size(max = 512, message = "Адрес не более 512 символов")
        String address
) {
}