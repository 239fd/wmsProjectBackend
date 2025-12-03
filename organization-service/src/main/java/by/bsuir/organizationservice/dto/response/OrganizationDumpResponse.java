package by.bsuir.organizationservice.dto.response;

import java.util.Map;

public record OrganizationDumpResponse(
        String message,
        Map<String, Object> organizationData,
        String dumpTimestamp
) {
}
