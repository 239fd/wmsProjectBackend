package by.bsuir.documentservice.rpa;

import by.bsuir.documentservice.config.RpaProperties;
import io.appium.java_client.windows.WindowsDriver;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rpa.office.enabled", havingValue = "true")
public class OfficeDocumentBot {

    private final RpaProperties props;

    public Path fillExcelTemplate(Path templatePath, Map<String, String> cells, String outputName)
            throws Exception {
        Path output = resolveOutputPath(templatePath, outputName);
        Files.createDirectories(output.getParent());
        Files.copy(templatePath, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setCapability("platformName", "windows");
        caps.setCapability("appium:automationName", "Windows");
        caps.setCapability("appium:app", props.getOffice().getExcelExe());
        caps.setCapability("appium:appArguments", "\"" + output.toAbsolutePath() + "\"");
        caps.setCapability("appium:appWorkingDir", output.getParent().toAbsolutePath().toString());

        WindowsDriver driver = new WindowsDriver(URI.create(props.getOffice().getDriverUrl()).toURL(), caps);
        try {
            Actions actions = new Actions(driver);
            new WebDriverWait(driver, Duration.ofSeconds(props.getOffice().getWaitSeconds()))
                    .until(d -> !d.findElements(By.name("Name Box")).isEmpty()
                            || !d.findElements(By.xpath("//*[@AutomationId='NameBox']")).isEmpty());

            for (Map.Entry<String, String> entry : cells.entrySet()) {
                WebElement nameBox = findNameBox(driver);
                nameBox.click();
                actions.keyDown(Keys.CONTROL).sendKeys("a").keyUp(Keys.CONTROL).perform();
                nameBox.sendKeys(entry.getKey() + Keys.ENTER);
                actions.sendKeys(safeValue(entry.getValue())).sendKeys(Keys.ENTER).perform();
            }

            actions.keyDown(Keys.CONTROL).sendKeys("s").keyUp(Keys.CONTROL).perform();
            Thread.sleep(1500);
            log.info("Excel: filled {} cells, saved to {}", cells.size(), output);
            return output;
        } finally {
            try { driver.quit(); } catch (Exception ex) { log.warn("driver.quit failed: {}", ex.getMessage()); }
        }
    }

    public Path fillWordTemplate(Path templatePath, Map<String, String> placeholders, String outputName)
            throws Exception {
        Path output = resolveOutputPath(templatePath, outputName);
        Files.createDirectories(output.getParent());
        Files.copy(templatePath, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setCapability("platformName", "windows");
        caps.setCapability("appium:automationName", "Windows");
        caps.setCapability("appium:app", props.getOffice().getWordExe());
        caps.setCapability("appium:appArguments", "\"" + output.toAbsolutePath() + "\"");

        WindowsDriver driver = new WindowsDriver(URI.create(props.getOffice().getDriverUrl()).toURL(), caps);
        try {
            Actions actions = new Actions(driver);
            new WebDriverWait(driver, Duration.ofSeconds(props.getOffice().getWaitSeconds()))
                    .until(d -> !d.findElements(By.name("Document")).isEmpty()
                            || !d.findElements(By.xpath("//Window")).isEmpty());

            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                replaceTokenInWord(actions, "{{" + entry.getKey() + "}}", safeValue(entry.getValue()));
            }

            actions.keyDown(Keys.CONTROL).sendKeys("s").keyUp(Keys.CONTROL).perform();
            Thread.sleep(1500);
            log.info("Word: replaced {} placeholders, saved to {}", placeholders.size(), output);
            return output;
        } finally {
            try { driver.quit(); } catch (Exception ex) { log.warn("driver.quit failed: {}", ex.getMessage()); }
        }
    }

    private void replaceTokenInWord(Actions actions, String token, String value) {
        actions.keyDown(Keys.CONTROL).sendKeys("h").keyUp(Keys.CONTROL).perform();
        try {
            Thread.sleep(800);
            actions.sendKeys(token).sendKeys(Keys.TAB).sendKeys(value).perform();
            actions.keyDown(Keys.ALT).sendKeys("a").keyUp(Keys.ALT).perform();
            Thread.sleep(500);
            actions.sendKeys(Keys.ESCAPE).perform();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    private WebElement findNameBox(WindowsDriver driver) {
        var byAutomationId = driver.findElements(By.xpath("//*[@AutomationId='NameBox']"));
        if (!byAutomationId.isEmpty()) return byAutomationId.get(0);
        return driver.findElement(By.name("Name Box"));
    }

    private Path resolveOutputPath(Path templatePath, String outputName) {
        String ext = extensionOf(templatePath, "bin");
        String safeName = sanitize(outputName) + "." + ext;
        return Paths.get(props.getOffice().getDownloadsDir()).resolve(safeName).toAbsolutePath();
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "office-" + UUID.randomUUID();
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String extensionOf(Path path, String fallback) {
        String name = path.getFileName().toString().toLowerCase();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1) : fallback;
    }

    private String safeValue(String raw) {
        if (raw == null) return "";
        return raw.replace("\n", " ").replace("\r", " ");
    }
}
