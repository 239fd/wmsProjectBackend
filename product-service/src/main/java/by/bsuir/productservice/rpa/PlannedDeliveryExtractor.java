package by.bsuir.productservice.rpa;

import java.util.List;
import java.util.Map;

public interface PlannedDeliveryExtractor {

    String getSourceName();

    List<Map<String, Object>> extractDeliveries();
}