package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.dto.request.CompleteOAuthRegistrationRequest;
import by.bsuir.ssoservice.dto.response.AuthResponse;
import by.bsuir.ssoservice.dto.response.InvitationValidationResponse;
import by.bsuir.ssoservice.dto.response.OAuthRegistrationResponse;
import by.bsuir.ssoservice.exception.AppException;
import by.bsuir.ssoservice.model.entity.OAuthPendingRegistration;
import by.bsuir.ssoservice.model.entity.UserReadModel;
import by.bsuir.ssoservice.model.enums.AuthProvider;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.repository.OAuthPendingRegistrationRepository;
import by.bsuir.ssoservice.repository.UserReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private static final String STATE_PREFIX = "oauth:state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final UserReadModelRepository userRepository;
    private final OAuthPendingRegistrationRepository pendingRegistrationRepository;
    private final UserService userService;
    private final InvitationValidationService invitationValidationService;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${oauth.yandex.client-id}")
    private String yandexClientId;

    @Value("${oauth.yandex.client-secret}")
    private String yandexClientSecret;

    @Value("${oauth.yandex.redirect-uri}")
    private String yandexRedirectUri;

    @Value("${oauth.google.client-id}")
    private String googleClientId;

    @Value("${oauth.google.client-secret}")
    private String googleClientSecret;

    @Value("${oauth.google.redirect-uri}")
    private String googleRedirectUri;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public String getAuthorizationUrl(String provider, String type) {
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(STATE_PREFIX + state, type == null ? "login" : type, STATE_TTL);
        return switch (provider.toLowerCase()) {
            case "yandex" -> buildYandexAuthUrl(state);
            case "google" -> buildGoogleAuthUrl(state);
            default -> throw new AppException("Неподдерживаемый провайдер: " + provider, HttpStatus.BAD_REQUEST);
        };
    }

    @Transactional
    public Object handleCallback(String provider, String code, String state, String ipAddress, String userAgent) {
        if (state == null || state.isBlank()) {
            throw new AppException("Отсутствует state-параметр OAuth", HttpStatus.BAD_REQUEST);
        }
        String stateKey = STATE_PREFIX + state;
        Object savedType = redisTemplate.opsForValue().get(stateKey);
        if (savedType == null) {
            throw new AppException("Недействительный или истёкший state OAuth", HttpStatus.BAD_REQUEST);
        }
        redisTemplate.delete(stateKey);

        String accessToken = exchangeCodeForToken(provider, code);
        OAuthUserInfo userInfo = getUserInfo(provider, accessToken);

        return userRepository.findByEmailAndProvider(userInfo.email(), AuthProvider.valueOf(provider.toUpperCase()))
                .map(existingUser -> (Object) userService.loginOAuthUser(existingUser, ipAddress, userAgent))
                .orElseGet(() -> (Object) createPendingRegistration(userInfo, provider));
    }

    @Transactional
    public AuthResponse completeRegistration(
            CompleteOAuthRegistrationRequest request,
            String ipAddress,
            String userAgent) {

        OAuthPendingRegistration pending = pendingRegistrationRepository
                .findByTemporaryTokenAndCompletedFalse(request.temporaryToken())
                .orElseThrow(() -> new AppException("Недействительный или истекший токен", HttpStatus.BAD_REQUEST));

        if (pending.isExpired()) {
            throw new AppException("Токен регистрации истек", HttpStatus.BAD_REQUEST);
        }

        UserRole role;
        UUID organizationUuid;
        UUID warehouseUuid;
        InvitationValidationResponse invitation = null;

        if (request.invitationToken() != null) {

            invitation = invitationValidationService.validateInvitation(request.invitationToken());
            if (invitation == null || !Boolean.TRUE.equals(invitation.valid())) {
                String msg = invitation == null ? "Ошибка валидации приглашения"
                        : (invitation.message() != null ? invitation.message() : "Приглашение недействительно");
                throw AppException.badRequest(msg);
            }
            if (invitation.email() != null && !invitation.email().equalsIgnoreCase(pending.getEmail())) {
                throw AppException.badRequest(
                        "Email OAuth-провайдера не совпадает с email в приглашении");
            }
            try {
                role = UserRole.valueOf(invitation.role());
            } catch (IllegalArgumentException e) {
                throw AppException.badRequest("Некорректная роль в приглашении: " + invitation.role());
            }
            if (role == UserRole.DIRECTOR) {
                throw AppException.badRequest(
                        "Роль DIRECTOR не может быть назначена через приглашение");
            }
            organizationUuid = invitation.organizationId();
            warehouseUuid = invitation.warehouseId();
        } else {
            if (request.role() == null) {
                throw AppException.badRequest(
                        "Роль обязательна при OAuth-регистрации без приглашения");
            }
            role = request.role();
            organizationUuid = request.organizationId() != null
                    ? UUID.fromString(request.organizationId()) : null;
            warehouseUuid = request.warehouseId() != null
                    ? UUID.fromString(request.warehouseId()) : null;
        }

        UserReadModel user = userService.createOAuthUser(
                pending.getEmail(),
                pending.getFullName(),
                pending.getProvider(),
                pending.getPhoto(),
                role,
                organizationUuid != null ? organizationUuid.toString() : null,
                warehouseUuid != null ? warehouseUuid.toString() : null
        );

        pending.setCompleted(true);
        pendingRegistrationRepository.save(pending);

        if (invitation != null) {

            invitationValidationService.addEmployeeToOrganization(
                    organizationUuid, user.getUserId(), role.name());
            invitationValidationService.markInvitationAsUsed(
                    request.invitationToken(), user.getUserId());
            log.info("OAuth user registered via invitation: {} (role={}, orgId={}, warehouseId={})",
                    user.getEmail(), role, organizationUuid, warehouseUuid);
        }

        return userService.generateTokensWithAudit(user, ipAddress, userAgent);
    }

    private OAuthRegistrationResponse createPendingRegistration(OAuthUserInfo userInfo, String provider) {
        pendingRegistrationRepository.findByEmailAndProviderAndCompletedFalse(userInfo.email(), provider)
                .ifPresent(pendingRegistrationRepository::delete);

        String temporaryToken = UUID.randomUUID().toString();

        OAuthPendingRegistration pending = OAuthPendingRegistration.builder()
                .temporaryToken(temporaryToken)
                .email(userInfo.email())
                .fullName(userInfo.name())
                .provider(provider)
                .providerUid(userInfo.id())
                .photo(userInfo.picture())
                .build();

        pendingRegistrationRepository.save(pending);

        return OAuthRegistrationResponse.builder()
                .temporaryToken(temporaryToken)
                .email(userInfo.email())
                .fullName(userInfo.name())
                .provider(provider)
                .requiresRoleSelection(true)
                .redirectUrl(frontendUrl + "/role")
                .build();
    }

    private String exchangeCodeForToken(String provider, String code) {
        return switch (provider.toLowerCase()) {
            case "yandex" -> exchangeYandexCode(code);
            case "google" -> exchangeGoogleCode(code);
            default -> throw new AppException("Неподдерживаемый провайдер", HttpStatus.BAD_REQUEST);
        };
    }

    private OAuthUserInfo getUserInfo(String provider, String accessToken) {
        return switch (provider.toLowerCase()) {
            case "yandex" -> getYandexUserInfo(accessToken);
            case "google" -> getGoogleUserInfo(accessToken);
            default -> throw new AppException("Неподдерживаемый провайдер", HttpStatus.BAD_REQUEST);
        };
    }

    private String buildYandexAuthUrl(String type) {
        return String.format(
                "https://oauth.yandex.ru/authorize?response_type=code&client_id=%s&redirect_uri=%s&state=%s",
                yandexClientId,
                yandexRedirectUri,
                type
        );
    }

    private String exchangeYandexCode(String code) {
        String tokenUrl = "https://oauth.yandex.ru/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(yandexClientId, yandexClientSecret);

        String body = String.format(
                "grant_type=authorization_code&code=%s&redirect_uri=%s",
                code,
                yandexRedirectUri
        );

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> responseBody = response.getBody();
            return (String) responseBody.get("access_token");
        } catch (Exception e) {
            log.error("Failed to exchange Yandex code for token", e);
            throw new AppException("Ошибка авторизации через Яндекс", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private OAuthUserInfo getYandexUserInfo(String accessToken) {
        String userInfoUrl = "https://login.yandex.ru/info?format=json";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    userInfoUrl,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> userInfo = response.getBody();
            return new OAuthUserInfo(
                    (String) userInfo.get("id"),
                    (String) userInfo.get("default_email"),
                    (String) userInfo.get("display_name"),
                    null
            );
        } catch (Exception e) {
            log.error("Failed to get Yandex user info", e);
            throw new AppException("Ошибка получения данных пользователя", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildGoogleAuthUrl(String type) {
        return String.format(
                "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=%s&redirect_uri=%s&scope=openid email profile&state=%s",
                googleClientId,
                googleRedirectUri,
                type
        );
    }

    private String exchangeGoogleCode(String code) {
        String tokenUrl = "https://oauth2.googleapis.com/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = String.format(
                "grant_type=authorization_code&code=%s&redirect_uri=%s&client_id=%s&client_secret=%s",
                code,
                googleRedirectUri,
                googleClientId,
                googleClientSecret
        );

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> responseBody = response.getBody();
            return (String) responseBody.get("access_token");
        } catch (Exception e) {
            log.error("Failed to exchange Google code for token", e);
            throw new AppException("Ошибка авторизации через Google", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private OAuthUserInfo getGoogleUserInfo(String accessToken) {
        String userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    userInfoUrl,
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> userInfo = response.getBody();
            return new OAuthUserInfo(
                    (String) userInfo.get("id"),
                    (String) userInfo.get("email"),
                    (String) userInfo.get("name"),
                    null
            );
        } catch (Exception e) {
            log.error("Failed to get Google user info", e);
            throw new AppException("Ошибка получения данных пользователя", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void cleanupExpiredRegistrations() {
        pendingRegistrationRepository.deleteExpiredOrCompleted(LocalDateTime.now());
    }

    private record OAuthUserInfo(String id, String email, String name, byte[] picture) {
    }
}
