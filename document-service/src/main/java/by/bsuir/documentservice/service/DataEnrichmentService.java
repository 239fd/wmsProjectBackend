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
                if (warehouse.get("address") != null) {
                    enriched.put("warehouseAddress", warehouse.get("address"));
                    enriched.putIfAbsent("loadingPoint", warehouse.get("address"));
                }
            }
        }

        UUID senderOrgId = parseUuid(data.get("senderOrganizationId"));
        UUID orgIdToFetch = organizationId != null ? organizationId : senderOrgId;
        if (orgIdToFetch != null && data.get("organizationName") == null) {
            Map<String, Object> org = organizationClient.getOrganization(orgIdToFetch);
            if (org != null) {
                Object name = org.get("name") != null ? org.get("name") : org.get("shortName");
                if (name != null) {
                    enriched.put("organizationName", name);
                    enriched.putIfAbsent("shipperName", name);
                    enriched.putIfAbsent("senderName", name);
                    enriched.putIfAbsent("releasedBy", name);
                }
                if (org.get("inn") != null) enriched.put("inn", org.get("inn"));
                if (org.get("unp") != null) {
                    enriched.put("unp", org.get("unp"));
                    enriched.putIfAbsent("shipperInn", org.get("unp"));
                    enriched.putIfAbsent("senderInn", org.get("unp"));
                }
                if (org.get("address") != null) {
                    enriched.put("organizationAddress", org.get("address"));
                    enriched.putIfAbsent("shipperAddress", org.get("address"));
                    enriched.putIfAbsent("senderAddress", org.get("address"));
                }
            }
        }

        Object recipientName = data.get("recipientName");
        if (recipientName != null) {
            enriched.putIfAbsent("consigneeName", recipientName);
            enriched.putIfAbsent("payerName", recipientName);
        }
        Object recipientAddress = data.get("recipientAddress");
        if (recipientAddress != null) {
            enriched.putIfAbsent("consigneeAddress", recipientAddress);
            enriched.putIfAbsent("deliveryPoint", recipientAddress);
        }
        Object recipientInn = data.get("recipientInn");
        if (recipientInn != null) {
            enriched.putIfAbsent("consigneeInn", recipientInn);
            enriched.putIfAbsent("payerInn", recipientInn);
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
