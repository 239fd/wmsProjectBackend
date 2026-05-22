package by.bsuir.productservice.dto.import_;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SalesOrderDto(
        String shipmentId,
        String externalId,
        String externalSource,
        LocalDate date,
        LocalDate expectedDate,
        String status,
        String currency,
        BigDecimal totalAmount,
        String operation,
        CustomerDto customer,
        OrganizationDto organization,
        List<SalesItemDto> shipmentItems) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CustomerDto(
            String name,
            String inn,
            String address,
            String phone,
            String email) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OrganizationDto(
            String name) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SalesItemDto(
            Integer rowNumber,
            String name,
            String unit,
            BigDecimal qty,
            BigDecimal unitPrice,
            BigDecimal totalAmount) { }
}
