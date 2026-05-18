package by.bsuir.documentservice.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record OfficeFillRequest(
        @NotBlank(message = "templateName обязателен — имя файла в documents template/")
        String templateName,

        String outputName,

        Map<String, String> cells,

        Map<String, String> placeholders
) {
}
