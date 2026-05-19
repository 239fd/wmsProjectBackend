package by.bsuir.productservice.controller;

import by.bsuir.productservice.client.DocumentClient;
import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.WriteOffRequest;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import by.bsuir.productservice.service.BarcodeService;
import by.bsuir.productservice.service.PlacementService;
import by.bsuir.productservice.service.ProductOperationService;
import by.bsuir.productservice.service.RevaluationService;
import by.bsuir.productservice.service.WriteOffService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperationController — модульные тесты")
class OperationControllerTest {

    @Mock private ProductOperationService operationService;
    @Mock private PlacementService placementService;
    @Mock private BarcodeService barcodeService;
    @Mock private RevaluationService revaluationService;
    @Mock private WriteOffService writeOffService;
    @Mock private DocumentClient documentClient;
    @Mock private ProductReadModelRepository productRepository;

    @InjectMocks private OperationController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @org.junit.jupiter.api.Disabled("Security flow moved to receipt-session — pending mock update for new ReceiptSessionController flow")
    @Test
    @DisplayName("POST /receive без X-User-Role → 403")
    void receive_GivenMissingRole_ShouldReturnForbidden() throws Exception {
        ReceiveProductRequest req = new ReceiveProductRequest(
                UUID.randomUUID(), null, UUID.randomUUID(), null,
                BigDecimal.TEN, UUID.randomUUID(), null, null);

        mockMvc.perform(post("/api/operations/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        verify(operationService, never()).receiveProduct(any(), any());
    }

    @org.junit.jupiter.api.Disabled("Endpoint /api/operations/receive deprecated by ReceiptSession flow — test pending rewrite")
    @Test
    @DisplayName("POST /receive с WORKER → вызывает receiveProduct и генерирует документ")
    void receive_GivenWorker_ShouldInvokeService() throws Exception {
        UUID orgId = UUID.randomUUID();
        ReceiveProductRequest req = new ReceiveProductRequest(
                UUID.randomUUID(), null, UUID.randomUUID(), null,
                BigDecimal.TEN, UUID.randomUUID(), null, null);
        when(operationService.receiveProduct(any(), any())).thenReturn(UUID.randomUUID());
        when(documentClient.fetch(any(), any(), any(), any())).thenReturn(new DocumentClient.Fetched(new byte[0], "auto"));

        mockMvc.perform(post("/api/operations/receive")
                        .header("X-User-Role", "WORKER")
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verify(operationService).receiveProduct(any(), any());
    }

    @Test
    @DisplayName("POST /write-off без X-User-Role → 403")
    void writeOff_GivenMissingRole_ShouldReturnForbidden() throws Exception {
        WriteOffRequest req = new WriteOffRequest(
                UUID.randomUUID(), UUID.randomUUID(), null, null,
                BigDecimal.ONE, "Просрочка", null, null, List.of(),
                UUID.randomUUID(), null);

        mockMvc.perform(post("/api/operations/write-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        verify(writeOffService, never()).writeOff(any(), any());
    }
}
