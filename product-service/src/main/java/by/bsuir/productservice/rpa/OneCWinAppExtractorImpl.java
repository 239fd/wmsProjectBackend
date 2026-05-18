package by.bsuir.productservice.rpa;

import by.bsuir.productservice.config.RpaProperties;
import io.appium.java_client.windows.WindowsDriver;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component("oneCExtractor")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rpa.onec.enabled", havingValue = "true")
public class OneCWinAppExtractorImpl implements PlannedDeliveryExtractor {

    private final RpaProperties props;

    @Override
    public String getSourceName() {
        return "1C-RPA";
    }

    @Override
    public List<Map<String, Object>> extractDeliveries() {
        log.info("1C-RPA: запускаю экстракцию (attachMode={}, база={}, журнал={})",
                props.getOnec().isAttachMode(),
                props.getOnec().getBasePath(),
                props.getOnec().getJournalName());

        WindowsDriver driver = props.getOnec().isAttachMode()
                ? attachToExistingWindow()
                : launchNewSession();

        try {
            if (!props.getOnec().isAttachMode()) {
                login(driver);
            }
            navigateToJournal(driver);
            return readJournalTable(driver);
        } finally {
            try { driver.quit(); } catch (Exception ex) {
                log.warn("1C-RPA: driver.quit ошибка: {}", ex.getMessage());
            }
        }
    }

    private WindowsDriver launchNewSession() {
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setCapability("platformName", "windows");
        caps.setCapability("appium:automationName", "Windows");
        caps.setCapability("appium:app", props.getOnec().getExecutable());
        caps.setCapability("appium:appArguments",
                "ENTERPRISE /F\"" + props.getOnec().getBasePath() + "\"");
        caps.setCapability("appium:waitForAppLaunch", 10);
        try {
            return new WindowsDriver(URI.create(props.getOnec().getDriverUrl()).toURL(), caps);
        } catch (Exception e) {
            log.error("1C-RPA: не удалось запустить WinAppDriver (launch): {}", e.getMessage(), e);
            throw new IllegalStateException("Launch failed: " + e.getMessage(), e);
        }
    }

    private WindowsDriver attachToExistingWindow() {
        String pattern = props.getOnec().getWindowTitlePattern();
        String hwndHex = findHwndByTitle(pattern);
        if (hwndHex == null) {
            throw new IllegalStateException(
                    "1C-RPA attach-mode: не найдено окно с заголовком, содержащим «" + pattern
                            + "». Запусти 1С руками до вызова extractor, либо переключи rpa.onec.attach-mode=false.");
        }
        log.info("1C-RPA: найдено окно 1С (hwnd={}), подключаюсь через appTopLevelWindow", hwndHex);

        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setCapability("platformName", "windows");
        caps.setCapability("appium:automationName", "Windows");
        caps.setCapability("appium:appTopLevelWindow", hwndHex);
        try {
            return new WindowsDriver(URI.create(props.getOnec().getDriverUrl()).toURL(), caps);
        } catch (Exception e) {
            log.error("1C-RPA: не удалось attach к окну: {}", e.getMessage(), e);
            throw new IllegalStateException("Attach failed: " + e.getMessage(), e);
        }
    }

    private String findHwndByTitle(String pattern) {
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setCapability("platformName", "windows");
        caps.setCapability("appium:automationName", "Windows");
        caps.setCapability("appium:app", "Root");

        WindowsDriver root;
        try {
            root = new WindowsDriver(URI.create(props.getOnec().getDriverUrl()).toURL(), caps);
        } catch (Exception e) {
            log.error("1C-RPA: не удалось открыть Root-session: {}", e.getMessage(), e);
            throw new IllegalStateException("Cannot open Root desktop session: " + e.getMessage(), e);
        }

        try {
            List<WebElement> candidates = root.findElements(
                    By.xpath("//Window[contains(@Name, '" + pattern + "')]"));
            log.info("1C-RPA: найдено окон-кандидатов: {} (pattern={})", candidates.size(), pattern);
            for (WebElement w : candidates) {
                String name = w.getAttribute("Name");
                String hwndDecimal = w.getAttribute("NativeWindowHandle");
                if (hwndDecimal == null) continue;
                log.info("1C-RPA: кандидат «{}» → hwnd_decimal={}", name, hwndDecimal);
                try {
                    long h = Long.parseLong(hwndDecimal);
                    return "0x" + Long.toHexString(h);
                } catch (NumberFormatException nfe) {
                    log.warn("1C-RPA: не parsing hwnd «{}»", hwndDecimal);
                }
            }
            return null;
        } finally {
            try { root.quit(); } catch (Exception ignored) { }
        }
    }

    private void login(WindowsDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(props.getOnec().getWaitSeconds()));
        String username = props.getOnec().getUsername();

        try {
            WebElement userField = wait.until(d -> firstAvailable(d,
                    By.name("Пользователь"),
                    By.xpath("//*[@AutomationId='UserNameField']"),
                    By.xpath("//ComboBox[1]"),
                    By.xpath("//Edit[1]")));
            userField.click();
            userField.sendKeys(Keys.CONTROL + "a");
            userField.sendKeys(username);

            if (props.getOnec().getPassword() != null && !props.getOnec().getPassword().isEmpty()) {
                WebElement passField = firstAvailable(driver,
                        By.name("Пароль"),
                        By.xpath("//*[@AutomationId='PasswordField']"),
                        By.xpath("//Edit[2]"));
                if (passField != null) {
                    passField.click();
                    passField.sendKeys(props.getOnec().getPassword());
                }
            }

            WebElement okBtn = firstAvailable(driver,
                    By.name("ОК"),
                    By.name("OK"),
                    By.name("Войти"),
                    By.xpath("//Button[@Name='ОК']"),
                    By.xpath("//Button[1]"));
            if (okBtn != null) {
                okBtn.click();
            } else {
                userField.sendKeys(Keys.ENTER);
            }

            log.info("1C-RPA: логин выполнен для {}", username);
        } catch (Exception e) {
            log.error("1C-RPA: не удалось залогиниться: {}", e.getMessage(), e);
            throw new IllegalStateException("Login failed: " + e.getMessage(), e);
        }
    }

    private void navigateToJournal(WindowsDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(props.getOnec().getWaitSeconds()));
        String section = props.getOnec().getSectionName();
        String journal = props.getOnec().getJournalName();

        try {
            WebElement sectionBtn = wait.until(d -> firstAvailable(d,
                    By.name(section),
                    By.xpath("//*[@Name='" + section + "']")));
            sectionBtn.click();
            Thread.sleep(800);

            WebElement journalBtn = firstAvailable(driver,
                    By.name(journal),
                    By.xpath("//*[@Name='" + journal + "']"));
            if (journalBtn == null) {
                throw new IllegalStateException("Журнал не найден: " + journal);
            }
            journalBtn.click();
            log.info("1C-RPA: открыт журнал «{} → {}»", section, journal);
            Thread.sleep(1500);
        } catch (Exception e) {
            log.error("1C-RPA: навигация к журналу не удалась: {}", e.getMessage(), e);
            throw new IllegalStateException("Navigation failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> readJournalTable(WindowsDriver driver) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<WebElement> rows = driver.findElements(
                    By.xpath("//*[@LocalizedControlType='элемент таблицы' or @LocalizedControlType='строка таблицы']"));
            if (rows.isEmpty()) {
                rows = driver.findElements(By.xpath("//DataItem"));
            }
            if (rows.isEmpty()) {
                rows = driver.findElements(By.xpath("//Custom[contains(@Name,'.')]"));
            }
            log.info("1C-RPA: найдено строк в таблице: {}", rows.size());

            int idx = 0;
            for (WebElement row : rows) {
                idx++;
                List<WebElement> cells = row.findElements(By.xpath("./*"));
                if (cells.size() < 3) continue;

                Map<String, Object> delivery = new HashMap<>();
                delivery.put("externalId", cellText(cells, 0, "1c-" + idx));
                delivery.put("expectedDate", cellText(cells, 1, null));
                delivery.put("supplierName", cellText(cells, 2, "—"));
                delivery.put("productName", cellText(cells, 3, "—"));
                delivery.put("expectedQuantity", cellText(cells, 4, "0"));
                result.add(delivery);
            }
        } catch (Exception e) {
            log.error("1C-RPA: ошибка при чтении таблицы: {}", e.getMessage(), e);
        }
        return result;
    }

    private String cellText(List<WebElement> cells, int idx, String fallback) {
        if (idx >= cells.size()) return fallback;
        try {
            String text = cells.get(idx).getText();
            return text != null && !text.isBlank() ? text.trim() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private WebElement firstAvailable(org.openqa.selenium.SearchContext ctx, By... locators) {
        for (By locator : locators) {
            try {
                List<WebElement> els = ctx.findElements(locator);
                if (!els.isEmpty()) return els.get(0);
            } catch (Exception ignored) { }
        }
        return null;
    }
}
