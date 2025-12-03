package by.bsuir.ssoservice.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Long expiresIn,
        String tokenType
) {
    public static AuthResponse of(String accessToken, String refreshToken, Long expiresIn) {
        return new AuthResponse(accessToken, refreshToken, expiresIn, "Bearer");
    }
}
