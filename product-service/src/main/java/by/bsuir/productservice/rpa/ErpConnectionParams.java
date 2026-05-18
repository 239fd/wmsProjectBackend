package by.bsuir.productservice.rpa;

public record ErpConnectionParams(
        String aggregator,
        String username,
        String password,
        String basePath,
        String sectionName,
        String journalName,
        String driverUrl
) {

    public static ErpConnectionParams empty() {
        return new ErpConnectionParams(null, null, null, null, null, null, null);
    }

    public boolean isEmpty() {
        return (aggregator == null || aggregator.isBlank())
                && (username == null || username.isBlank())
                && (password == null || password.isBlank())
                && (basePath == null || basePath.isBlank())
                && (sectionName == null || sectionName.isBlank())
                && (journalName == null || journalName.isBlank())
                && (driverUrl == null || driverUrl.isBlank());
    }
}
