package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.config.RabbitMQConfig;
import by.bsuir.organizationservice.exception.AppException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseClientService {

    private final RabbitTemplate rabbitTemplate;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getWarehousesByOrganization(UUID orgId) {
        log.info("Requesting warehouses for organization: {}", orgId);

        Map<String, String> request = new HashMap<>();
        request.put("orgId", orgId.toString());
        request.put("requestType", "GET_WAREHOUSES_BY_ORG");

        try {

            Object response = rabbitTemplate.convertSendAndReceive(
                    RabbitMQConfig.WAREHOUSE_EXCHANGE,
                    RabbitMQConfig.WAREHOUSE_INFO_REQUEST_KEY,
                    request
            );

            if (response == null) {
                log.warn("No response from warehouse service for org: {}", orgId);
                return Collections.emptyList();
            }

            log.info("Received response from warehouse service: {}", response);

            if (response instanceof List) {
                return (List<Map<String, Object>>) response;
            } else if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                if (responseMap.containsKey("warehouses")) {
                    return (List<Map<String, Object>>) responseMap.get("warehouses");
                }
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error requesting warehouses for org {}: {}", orgId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getWarehouseInfo(UUID warehouseId) {
        log.info("Requesting warehouse info: {}", warehouseId);

        Map<String, String> request = new HashMap<>();
        request.put("warehouseId", warehouseId.toString());
        request.put("requestType", "GET_WAREHOUSE_INFO");

        try {

            Object response = rabbitTemplate.convertSendAndReceive(
                    RabbitMQConfig.WAREHOUSE_EXCHANGE,
                    RabbitMQConfig.WAREHOUSE_INFO_REQUEST_KEY,
                    request
            );

            if (response == null) {
                throw AppException.notFound("Склад не найден");
            }

            log.info("Received warehouse info response: {}", response);

            return (Map<String, Object>) response;
        } catch (Exception e) {
            log.error("Error requesting warehouse info {}: {}", warehouseId, e.getMessage(), e);
            throw AppException.internalError("Не удалось получить информацию о складе");
        }
    }
}
