package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank(message = "Название товара обязательно")
        @Size(max = 255, message = "Название не должно превышать 255 символов")
        String name,

        @Size(max = 100, message = "SKU не должен превышать 100 символов")
        String sku,

        @Size(max = 100, message = "Штрих-код не должен превышать 100 символов")
        String barcode,

        @Size(max = 100, message = "Категория не должна превышать 100 символов")
        String category,

        String description,

        @Size(max = 50, message = "Единица измерения не должна превышать 50 символов")
        String unitOfMeasure,

        BigDecimal weightKg,

        BigDecimal volumeM3
) {
}
