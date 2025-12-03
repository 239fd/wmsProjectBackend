package by.bsuir.organizationservice.service;

import by.bsuir.organizationservice.config.RabbitMQConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseClientService Unit Tests")
class WarehouseClientServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private WarehouseClientService warehouseClientService;

    @Test
    @DisplayName("getWarehousesByOrganization: Given valid orgId Should return list of warehouses")
    void getWarehousesByOrganization_GivenValidOrgId_ShouldReturnListOfWarehouses() {
        UUID orgId = UUID.randomUUID();
        List<Map<String, Object>> expectedWarehouses = List.of(
                Map.of("warehouseId", UUID.randomUUID().toString(), "name", "Warehouse 1"),
                Map.of("warehouseId", UUID.randomUUID().toString(), "name", "Warehouse 2")
        );

        when(rabbitTemplate.convertSendAndReceive(
                eq(RabbitMQConfig.WAREHOUSE_EXCHANGE),
                eq(RabbitMQConfig.WAREHOUSE_INFO_REQUEST_KEY),
                any(Map.class)
        )).thenReturn(expectedWarehouses);

        List<Map<String, Object>> result = warehouseClientService.getWarehousesByOrganization(orgId);

        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(expectedWarehouses);
        verify(rabbitTemplate).convertSendAndReceive(
                eq(RabbitMQConfig.WAREHOUSE_EXCHANGE),
                eq(RabbitMQConfig.WAREHOUSE_INFO_REQUEST_KEY),
                any(Map.class)
        );
    }

    @Test
    @DisplayName("getWarehousesByOrganization: Given null response Should return empty list")
    void getWarehousesByOrganization_GivenNullResponse_ShouldReturnEmptyList() {
        UUID orgId = UUID.randomUUID();

        when(rabbitTemplate.convertSendAndReceive(
                eq(RabbitMQConfig.WAREHOUSE_EXCHANGE),
                eq(RabbitMQConfig.WAREHOUSE_INFO_REQUEST_KEY),
                any(Map.class)
        )).thenReturn(null);

        List<Map<String, Object>> result = warehouseClientService.getWarehousesByOrganization(orgId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getWarehousesByOrganization: Given Map response with warehouses Should return list")
    void getWarehousesByOrganization_GivenMapResponseWithWarehouses_ShouldReturnList() {
        UUID orgId = UUID.randomUUID();
        List<Map<String, Object>> warehouses = List.of(
                Map.of("warehouseId", UUID.randomUUID().toString(), "name", "Warehouse 1")
        );
        Map<String, Object> response = Map.of("warehouses", warehouses);

        when(rabbitTemplate.convertSendAndReceive(
                eq(RabbitMQConfig.WAREHOUSE_EXCHANGE),
                eq(RabbitMQConfig.WAREHOUSE_INFO_REQUEST_KEY),
                any(Map.class)
        )).thenReturn(response);

        List<Map<String, Object>> result = warehouseClientService.getWarehousesByOrganization(orgId);

        assertThat(result).hasSize(1);
        assertThat(result).isEqualTo(warehouses);
    }

    @Test
    @DisplayName("getWarehousesByOrganization: Given exception Should return empty list")
    void getWarehousesByOrganization_GivenException_ShouldReturnEmptyList() {
        UUID orgId = UUID.randomUUID();

        when(rabbitTemplate.convertSendAndReceive(
                eq(RabbitMQConfig.WAREHOUSE_EXCHANGE),
                eq(RabbitMQConfig.WAREHOUSE_INFO_REQUEST_KEY),
                any(Map.class)
        )).thenThrow(new RuntimeException("Connection failed"));

        List<Map<String, Object>> result = warehouseClientService.getWarehousesByOrganization(orgId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getWarehouseInfo: Given valid warehouseId Should return warehouse info")
    void getWarehouseInfo_GivenValidWarehouseId_ShouldReturnWarehouseInfo() {
        UUID warehouseId = UUID.randomUUID();
        Map<String, Object> expectedInfo = Map.of(
                "warehouseId", warehouseId.toString(),
                "name", "Main Warehouse",
                "address", "123 Main St"
        );

        when(rabbitTemplate.convertSendAndReceive(
                eq(RabbitMQConfig.WAREHOUSE_EXCHANGE),
                eq(RabbitMQConfig.WAREHOUSE_INFO_REQUEST_KEY),
                any(Map.class)
        )).thenReturn(expectedInfo);

        Map<String, Object> result = warehouseClientService.getWarehouseInfo(warehouseId);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(expectedInfo);
        verify(rabbitTemplate).convertSendAndReceive(
                eq(RabbitMQConfig.WAREHOUSE_EXCHANGE),
                eq(RabbitMQConfig.WAREHOUSE_INFO_REQUEST_KEY),
                any(Map.class)
        );
    }
}

