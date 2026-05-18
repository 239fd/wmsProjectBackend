package by.bsuir.documentservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockErpController Tests")
class MockErpControllerTest {

    private final MockErpController controller = new MockErpController();

    @Test
    @DisplayName("loginPage: возвращает HTML-форму с input username/password")
    void loginPage_whenCalled_thenReturnsHtmlForm() {
        String html = controller.loginPage();

        assertThat(html).contains("<form").contains("username").contains("password");
    }

    @Test
    @DisplayName("login: admin/admin → 200 OK с token")
    void login_givenAdminCreds_whenCalled_thenReturnsToken() {
        ResponseEntity<Map<String, String>> response = controller.login("admin", "admin");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsKey("token");
    }

    @Test
    @DisplayName("login: неверные креды → 401")
    void login_givenInvalidCreds_whenCalled_thenReturns401() {
        ResponseEntity<Map<String, String>> response = controller.login("admin", "wrong");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).containsKey("error");
    }

    @Test
    @DisplayName("deliveriesPage: возвращает HTML-таблицу с deliveries-table")
    void deliveriesPage_whenCalled_thenReturnsHtmlTable() {
        String html = controller.deliveriesPage(null, null);

        assertThat(html).contains("deliveries-table").contains("ERP-001").contains("Молоко");
    }

    @Test
    @DisplayName("deliveriesApi: правильный token → 200 OK со списком")
    void deliveriesApi_givenValidToken_whenCalled_thenReturns200() {
        ResponseEntity<?> response = controller.deliveriesApi("Bearer mock-erp-token-2024");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(java.util.List.class);
    }

    @Test
    @DisplayName("deliveriesApi: без auth → 401")
    void deliveriesApi_givenNoAuth_whenCalled_thenReturns401() {
        ResponseEntity<?> response = controller.deliveriesApi(null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("deliveriesApi: неверный token → 401")
    void deliveriesApi_givenWrongToken_whenCalled_thenReturns401() {
        ResponseEntity<?> response = controller.deliveriesApi("Bearer other-token");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}
