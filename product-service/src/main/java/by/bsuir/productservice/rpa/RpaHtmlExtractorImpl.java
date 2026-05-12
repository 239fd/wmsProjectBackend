package by.bsuir.productservice.rpa;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("rpaExtractor")
public class RpaHtmlExtractorImpl implements PlannedDeliveryExtractor {

    @Value("${erp.rpa.base-url:http://localhost:8040/mock-erp}")
    private String erpBaseUrl;

    @Value("${erp.rpa.username:admin}")
    private String erpUsername;

    @Value("${erp.rpa.password:admin}")
    private String erpPassword;

    @Value("${erp.rpa.timeout-ms:10000}")
    private int timeoutMs;

    @Override
    public String getSourceName() {
        return "RPA";
    }

    @Override
    public List<Map<String, Object>> extractDeliveries() {
        log.info("RPA-экстрактор: начало скрейпинга ERP по адресу {}", erpBaseUrl);

        try {
            Map<String, String> cookies = performLogin();
            log.info("RPA-экстрактор: логин выполнен, cookies: {}", cookies.keySet());

            String html = fetchDeliveriesPage(cookies);
            List<Map<String, Object>> deliveries = parseDeliveriesTable(html);

            log.info("RPA-экстрактор: извлечено {} записей из HTML-таблицы", deliveries.size());
            return deliveries;

        } catch (Exception e) {
            log.error("RPA-экстрактор: ошибка скрейпинга: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка RPA-экстрактора: " + e.getMessage(), e);
        }
    }

    private Map<String, String> performLogin() throws Exception {
        Connection.Response loginResponse = Jsoup.connect(erpBaseUrl + "/login")
                .method(Connection.Method.POST)
                .data("username", erpUsername)
                .data("password", erpPassword)
                .timeout(timeoutMs)
                .ignoreContentType(true)
                .execute();

        return loginResponse.cookies();
    }

    private String fetchDeliveriesPage(Map<String, String> cookies) throws Exception {
        Connection.Response response = Jsoup.connect(erpBaseUrl + "/deliveries")
                .cookies(cookies)
                .timeout(timeoutMs)
                .ignoreContentType(true)
                .execute();

        return response.body();
    }

    private List<Map<String, Object>> parseDeliveriesTable(String html) {
        Document doc = Jsoup.parse(html);
        Element table = doc.select("table#deliveries-table").first();
        List<Map<String, Object>> result = new ArrayList<>();

        if (table == null) {
            log.warn("RPA-экстрактор: таблица поставок не найдена в HTML");
            return result;
        }

        Elements rows = table.select("tbody tr");
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 5) continue;

            Map<String, Object> delivery = new HashMap<>();
            delivery.put("externalId", cells.get(0).text().trim());
            delivery.put("supplierName", cells.get(1).text().trim());
            delivery.put("productName", cells.get(2).text().trim());
            delivery.put("expectedQuantity", parseIntSafe(cells.get(3).text().trim()));
            delivery.put("expectedDate", cells.get(4).text().trim());
            result.add(delivery);
        }

        return result;
    }

    private int parseIntSafe(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}