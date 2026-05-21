package by.bsuir.productservice.rpa;

import by.bsuir.productservice.dto.import_.SupplyDto;

import java.util.List;

public interface SupplyExtractor {

    String getSourceName();

    List<SupplyDto> extractSupplies();
}
