package by.bsuir.ssoservice.dto.response;

import lombok.Builder;





@Builder
public record OAuthRegistrationResponse(
        String temporaryToken,
        String email,
        String fullName,
        String provider,
        boolean requiresRoleSelection,
        String redirectUrl
) {
}
