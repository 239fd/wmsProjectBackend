package by.bsuir.documentservice.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/mock-erp")
@Hidden
public class MockErpController {

    private static final String DEMO_TOKEN = "mock-erp-token-2024";

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage() {
        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head><meta charset="UTF-8"><title>ERP Login</title></head>
                <body>
                  <h2>Вход в ERP-систему</h2>
                  <form method="POST" action="/mock-erp/login">
                    <label>Логин: <input name="username" value="admin"/></label><br/>
                    <label>Пароль: <input name="password" type="password" value="admin"/></label><br/>
                    <button type="submit">Войти</button>
                  </form>
                </body>
                </html>
                """;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestParam String username,
            @RequestParam String password) {
        if ("admin".equals(username) && "admin".equals(password)) {
            log.info("Mock ERP: успешный логин пользователя {}", username);
            return ResponseEntity.ok(Map.of("token", DEMO_TOKEN, "message", "Успешный вход"));
        }
        return ResponseEntity.status(401).body(Map.of("error", "Неверные учётные данные"));
    }

    @GetMapping(value = "/deliveries", produces = MediaType.TEXT_HTML_VALUE)
    public String deliveriesPage(@RequestHeader(value = "Authorization", required = false) String auth,
                                  @RequestParam(required = false) String token) {
        String rows = buildDeliveryRows();
        return """
                <!DOCTYPE html>
                <html lang="ru">
                <head><meta charset="UTF-8"><title>ERP — Плановые поставки</title></head>
                <body>
                  <h2>Плановые поставки</h2>
                  <table id="deliveries-table" border="1">
                    <thead>
                      <tr>
                        <th>ID</th><th>Поставщик</th><th>Товар</th>
                        <th>Кол-во</th><th>Дата поставки</th>
                      </tr>
                    </thead>
                    <tbody>
                """ + rows + """
                    </tbody>
                  </table>
                </body>
                </html>
                """;
    }

    @GetMapping("/api/deliveries")
    public ResponseEntity<?> deliveriesApi(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth == null || !auth.contains(DEMO_TOKEN)) {
            return ResponseEntity.status(401).body(Map.of("error", "Необходима авторизация"));
        }
        log.info("Mock ERP API: запрос плановых поставок");
        return ResponseEntity.ok(buildDeliveryList());
    }

    private List<Map<String, Object>> buildDeliveryList() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate nextWeek = LocalDate.now().plusDays(7);
        LocalDate twoWeeks = LocalDate.now().plusDays(14);
        return List.of(
                delivery("ERP-001", "ООО ПоставщикАльфа", "Яблоки свежие", 500, tomorrow),
                delivery("ERP-002", "ИП Бета", "Молоко 3.2%", 200, tomorrow),
                delivery("ERP-003", "ЗАО Гамма", "Хлеб ржаной", 150, nextWeek),
                delivery("ERP-004", "ООО ПоставщикАльфа", "Апельсины", 300, nextWeek),
                delivery("ERP-005", "ИП Дельта", "Сахар-песок", 1000, twoWeeks),
                delivery("ERP-006", "ЗАО Эпсилон", "Масло подсолнечное", 250, twoWeeks)
        );
    }

    private Map<String, Object> delivery(String id, String supplier, String product,
                                          int qty, LocalDate date) {
        return Map.of(
                "externalId", id,
                "supplierName", supplier,
                "productName", product,
                "expectedQuantity", qty,
                "expectedDate", date.toString()
        );
    }

    private String buildDeliveryRows() {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> d : buildDeliveryList()) {
            sb.append("<tr>");
            sb.append("<td>").append(d.get("externalId")).append("</td>");
            sb.append("<td>").append(d.get("supplierName")).append("</td>");
            sb.append("<td>").append(d.get("productName")).append("</td>");
            sb.append("<td>").append(d.get("expectedQuantity")).append("</td>");
            sb.append("<td>").append(d.get("expectedDate")).append("</td>");
            sb.append("</tr>\n");
        }
        return sb.toString();
    }
}