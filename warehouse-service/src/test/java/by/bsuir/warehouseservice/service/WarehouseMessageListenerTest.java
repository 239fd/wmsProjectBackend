package by.bsuir.warehouseservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseMessageListener Unit Tests")
class WarehouseMessageListenerTest {

    @Mock
    private WarehouseService warehouseService;

    @InjectMocks
    private WarehouseMessageListener messageListener;

    @Test
    @DisplayName("handleWarehouseInfoRequest: GET_WAREHOUSES_BY_ORG Should return warehouses")
    void handleWarehouseInfoRequest_GetWarehousesByOrg_ShouldReturnWarehouses() {
        UUID orgId = UUID.randomUUID();
        Map<String, String> request = new HashMap<>();
        request.put("requestType", "GET_WAREHOUSES_BY_ORG");
        request.put("orgId", orgId.toString());

        List<Map<String, Object>> mockWarehouses = List.of(
                Map.of("warehouseId", UUID.randomUUID().toString(), "name", "Warehouse 1")
        );

        when(warehouseService.getWarehousesInfoByOrganization(orgId)).thenReturn(mockWarehouses);

        Object result = messageListener.handleWarehouseInfoRequest(request);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result;
        assertThat(responseMap).containsKey("warehouses");
        assertThat(responseMap).containsKey("orgId");
        verify(warehouseService).getWarehousesInfoByOrganization(orgId);
    }

    @Test
    @DisplayName("handleWarehouseInfoRequest: GET_WAREHOUSE_INFO Should return warehouse info")
    void handleWarehouseInfoRequest_GetWarehouseInfo_ShouldReturnWarehouseInfo() {
        UUID warehouseId = UUID.randomUUID();
        Map<String, String> request = new HashMap<>();
        request.put("requestType", "GET_WAREHOUSE_INFO");
        request.put("warehouseId", warehouseId.toString());

        Map<String, Object> mockWarehouseInfo = Map.of(
                "warehouseId", warehouseId.toString(),
                "name", "Test Warehouse"
        );

        when(warehouseService.getWarehouseInfo(warehouseId)).thenReturn(mockWarehouseInfo);

        Object result = messageListener.handleWarehouseInfoRequest(request);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result;
        assertThat(responseMap).containsEntry("warehouseId", warehouseId.toString());
        assertThat(responseMap).containsEntry("name", "Test Warehouse");
        verify(warehouseService).getWarehouseInfo(warehouseId);
    }

    @Test
    @DisplayName("handleWarehouseInfoRequest: Unknown request type Should return error")
    void handleWarehouseInfoRequest_UnknownRequestType_ShouldReturnError() {
        Map<String, String> request = new HashMap<>();
        request.put("requestType", "UNKNOWN_TYPE");

        Object result = messageListener.handleWarehouseInfoRequest(request);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result;
        assertThat(responseMap).containsKey("error");
        verify(warehouseService, never()).getWarehousesInfoByOrganization(any());
        verify(warehouseService, never()).getWarehouseInfo(any());
    }

    @Test
    @DisplayName("handleWarehouseInfoRequest: Exception Should return error")
    void handleWarehouseInfoRequest_Exception_ShouldReturnError() {
        UUID warehouseId = UUID.randomUUID();
        Map<String, String> request = new HashMap<>();
        request.put("requestType", "GET_WAREHOUSE_INFO");
        request.put("warehouseId", warehouseId.toString());

        when(warehouseService.getWarehouseInfo(warehouseId))
                .thenThrow(new RuntimeException("Test error"));

        Object result = messageListener.handleWarehouseInfoRequest(request);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) result;
        assertThat(responseMap).containsKey("error");
    }

    @Test
    @DisplayName("handleOrganizationDeleted: Should delete warehouses for organization")
    void handleOrganizationDeleted_ShouldDeleteWarehousesForOrganization() {
        UUID orgId = UUID.randomUUID();
        Map<String, Object> message = new HashMap<>();
        message.put("orgId", orgId.toString());

        doNothing().when(warehouseService).deleteWarehousesByOrganization(orgId);

        messageListener.handleOrganizationDeleted(message);

        verify(warehouseService).deleteWarehousesByOrganization(orgId);
    }

    @Test
    @DisplayName("handleOrganizationDeleted: Exception Should be logged")
    void handleOrganizationDeleted_Exception_ShouldBeLogged() {
        UUID orgId = UUID.randomUUID();
        Map<String, Object> message = new HashMap<>();
        message.put("orgId", orgId.toString());

        doThrow(new RuntimeException("Test error"))
                .when(warehouseService).deleteWarehousesByOrganization(orgId);

        messageListener.handleOrganizationDeleted(message);

        verify(warehouseService).deleteWarehousesByOrganization(orgId);
    }
}

