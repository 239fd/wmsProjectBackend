package by.bsuir.documentservice.service;

import by.bsuir.documentservice.client.OrganizationClient;
import by.bsuir.documentservice.client.ProductClient;
import by.bsuir.documentservice.client.WarehouseClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataEnrichmentService {

    private final ProductClient productClient;
    private final WarehouseClient warehouseClient;
    private final OrganizationClient organizationClient;

    public Map<String, Object> enrich(Map<String, Object> data, UUID organizationId) {
        Map<String, Object> enriched = new HashMap<>(data);

        UUID productId = parseUuid(data.get("productId"));
        if (productId != null && data.get("productName") == null) {
            Map<String, Object> product = productClient.getProduct(productId, organizationId);
            if (product != null) {
                if (product.get("name") != null) enriched.put("productName", product.get("name"));
                if (product.get("sku") != null) enriched.put("sku", product.get("sku"));
                if (product.get("price") != null) enriched.put("unitPrice", product.get("price"));
            }
        }

        UUID batchId = parseUuid(data.get("batchId"));
        if (batchId != null && data.get("batchNumber") == null) {
            Map<String, Object> batch = productClient.getBatch(batchId, organizationId);
            if (batch != null) {
                if (batch.get("batchNumber") != null) enriched.put("batchNumber", batch.get("batchNumber"));
                if (batch.get("expiryDate") != null) enriched.put("batchExpiry", batch.get("expiryDate"));
                if (batch.get("storageConditions") != null) enriched.put("storageConditions", batch.get("storageConditions"));
            }
        }

        UUID warehouseId = parseUuid(data.get("warehouseId"));
        if (warehouseId != null && data.get("warehouseName") == null) {
            Map<String, Object> warehouse = warehouseClient.getWarehouse(warehouseId);
            if (warehouse != null) {
                if (warehouse.get("name") != null) enriched.put("warehouseName", warehouse.get("name"));
                if (warehouse.get("address") != null) enriched.put("warehouseAddress", warehouse.get("address"));
            }
        }

        if (organizationId != null && data.get("organizationName") == null) {
            Map<String, Object> org = organizationClient.getOrganization(organizationId);
            if (org != null) {
                if (org.get("name") != null) enriched.put("organizationName", org.get("name"));
                if (org.get("inn") != null) enriched.put("inn", org.get("inn"));
                if (org.get("unp") != null) enriched.put("unp", org.get("unp"));
            }
        }

        return enriched;
    }

    private UUID parseUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
