package by.bsuir.warehouseservice.integration;

import org.testcontainers.DockerClientFactory;

public final class DockerAvailability {

    private static volatile Boolean cached;

    private DockerAvailability() {}

    public static boolean dockerAvailable() {
        if (cached != null) return cached;
        synchronized (DockerAvailability.class) {
            if (cached != null) return cached;
            try {
                cached = DockerClientFactory.instance().isDockerAvailable();
            } catch (Throwable t) {
                cached = false;
            }
            return cached;
        }
    }
}
