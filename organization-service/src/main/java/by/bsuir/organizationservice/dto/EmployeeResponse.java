package by.bsuir.organizationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeResponse {
    private UUID userId;
    private UUID orgId;
    private String username;
    private String email;
    private String role;
    private LocalDateTime joinedAt;
}

