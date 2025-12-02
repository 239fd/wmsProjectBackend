package by.bsuir.warehouseservice.service;

import by.bsuir.warehouseservice.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseMessageListener {

    private final WarehouseService warehouseService;





    @RabbitListener(queues = RabbitMQConfig.WAREHOUSE_INFO_REQUEST_QUEUE)
    public Object handleWarehouseInfoRequest(@Payload Map<String, String> request) {
        log.info("Received warehouse info request: {}", request);

        try {
            String requestType = request.get("requestType");

            if ("GET_WAREHOUSES_BY_ORG".equals(requestType)) {
                UUID orgId = UUID.fromString(request.get("orgId"));
                List<Map<String, Object>> warehouses = warehouseService.getWarehousesInfoByOrganization(orgId);

                Map<String, Object> responseMap = new HashMap<>();
                responseMap.put("warehouses", warehouses);
                responseMap.put("orgId", orgId.toString());

                log.info("Sending response with {} warehouses for org: {}", warehouses.size(), orgId);
                return responseMap;

            } else if ("GET_WAREHOUSE_INFO".equals(requestType)) {
                UUID warehouseId = UUID.fromString(request.get("warehouseId"));
                Map<String, Object> response = warehouseService.getWarehouseInfo(warehouseId);

                log.info("Sending warehouse info response for: {}", warehouseId);
                return response;

            } else {
                log.warn("Unknown request type: {}", requestType);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Unknown request type");
                return errorResponse;
            }

        } catch (Exception e) {
            log.error("Error processing warehouse info request: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }




    @RabbitListener(queues = RabbitMQConfig.ORGANIZATION_DELETED_QUEUE)
    public void handleOrganizationDeleted(@Payload Map<String, Object> message) {
        log.info("Received organization.deleted event: {}", message);

        try {
            String orgIdStr = message.get("orgId").toString();
            UUID orgId = UUID.fromString(orgIdStr);

            warehouseService.deleteWarehousesByOrganization(orgId);

            log.info("Successfully processed organization.deleted event for orgId: {}", orgId);

        } catch (Exception e) {
            log.error("Error processing organization.deleted event: {}", e.getMessage(), e);
        }
    }
}
