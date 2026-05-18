package by.bsuir.organizationservice.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        File envFile = findDotEnv();
        if (envFile == null) {
            System.out.println("[DotEnv] .env not found in working dir or 5 parents — skipping");
            return;
        }
        try {
            Map<String, Object> values = new HashMap<>();
            List<String> lines = Files.readAllLines(envFile.toPath());
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int idx = line.indexOf('=');
                if (idx <= 0) continue;
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (System.getenv(key) == null && !environment.containsProperty(key)) {
                    values.put(key, value);
                }
            }
            if (!values.isEmpty()) {
                environment.getPropertySources()
                        .addFirst(new MapPropertySource("dotEnvFile", values));
                System.out.println("[DotEnv] Loaded " + values.size() + " entries from " + envFile.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("[DotEnv] Failed to load " + envFile.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private File findDotEnv() {
        File current = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && current != null; i++) {
            File candidate = new File(current, ".env");
            if (candidate.isFile()) return candidate;
            current = current.getParentFile();
        }
        return null;
    }
}
