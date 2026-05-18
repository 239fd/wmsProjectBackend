package by.bsuir.productservice.controller;

import by.bsuir.productservice.service.ProductJourneyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCardController Tests")
class ProductCardControllerTest {

    @Mock
    private ProductJourneyService productJourneyService;

    @InjectMocks
    private ProductCardController controller;

    @Test
    @DisplayName("getProductCard: WORKER → 200 OK с JSON map")
    void getProductCard_givenWorkerRole_whenCalled_thenReturnsJsonMap() {
        UUID productId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(productJourneyService.getJourney(eq(productId), any(), any(), eq(org)))
                .thenReturn(Map.of("product", "x"));

        ResponseEntity<Map<String, Object>> response =
                controller.getProductCard(productId, null, null, "WORKER", org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("product");
    }

    @Test
    @DisplayName("getProductCard: null role → 403 Forbidden")
    void getProductCard_givenNullRole_whenCalled_thenForbidden() {
        ResponseEntity<Map<String, Object>> response =
                controller.getProductCard(UUID.randomUUID(), null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getProductCardPdf: WORKER → 200 OK + APPLICATION_PDF + Content-Disposition")
    void getProductCardPdf_givenWorkerRole_whenCalled_thenReturnsPdf() {
        UUID productId = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(productJourneyService.generateJourneyPdf(eq(productId), any(), any(), eq(org)))
                .thenReturn(new byte[]{1, 2, 3});

        ResponseEntity<byte[]> response =
                controller.getProductCardPdf(productId, null, null, "WORKER", org);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("product-card.pdf");
        assertThat(response.getBody()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("getProductCardPdf: null role → 403 Forbidden")
    void getProductCardPdf_givenNullRole_whenCalled_thenForbidden() {
        ResponseEntity<byte[]> response =
                controller.getProductCardPdf(UUID.randomUUID(), null, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
