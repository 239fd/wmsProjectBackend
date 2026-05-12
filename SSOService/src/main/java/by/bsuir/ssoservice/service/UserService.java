package by.bsuir.ssoservice.service;

import by.bsuir.ssoservice.dto.request.LoginRequest;
import by.bsuir.ssoservice.dto.request.RegisterDirectorRequest;
import by.bsuir.ssoservice.dto.request.RegisterWithInvitationRequest;
import by.bsuir.ssoservice.dto.response.InvitationValidationResponse;
import by.bsuir.ssoservice.dto.response.AuthResponse;
import by.bsuir.ssoservice.dto.response.UserResponse;
import by.bsuir.ssoservice.exception.AppException;
import by.bsuir.ssoservice.model.entity.LoginAudit;
import by.bsuir.ssoservice.model.entity.UserEvent;
import by.bsuir.ssoservice.model.entity.UserReadModel;
import by.bsuir.ssoservice.model.enums.AuthProvider;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.model.event.UserEvents;
import by.bsuir.ssoservice.repository.LoginAuditRepository;
import by.bsuir.ssoservice.repository.UserEventRepository;
import by.bsuir.ssoservice.repository.UserReadModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserEventRepository eventRepository;
    private final UserReadModelRepository readModelRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAuditRepository loginAuditRepository;
    private final ObjectMapper objectMapper;
    private final InvitationValidationService invitationValidationService;

    @Transactional
    public AuthResponse registerDirector(RegisterDirectorRequest request, String ipAddress, String userAgent) {
        if (readModelRepository.existsByEmail(request.email())) {
            throw AppException.conflict("Пользователь с таким email уже существует");
        }

        UUID userId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(request.password());

        UserEvents.UserCreatedEvent event = new UserEvents.UserCreatedEvent(
                request.email(),
                request.getFullName(),
                UserRole.DIRECTOR,
                passwordHash,
                AuthProvider.LOCAL,
                null,
                null
        );

        UserEvent userEvent = UserEvent.builder()
                .userId(userId)
                .eventType("USER_CREATED")
                .eventData(objectMapper.valueToTree(event))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(userEvent);

        UserReadModel readModel = UserReadModel.builder()
                .userId(userId)
                .email(request.email())
                .fullName(request.getFullName())
                .role(UserRole.DIRECTOR)
                .passwordHash(passwordHash)
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        readModelRepository.save(readModel);

        log.info("Director registered: {} (userId={})", request.email(), userId);
        return generateTokensWithAudit(readModel, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse registerWithInvitation(RegisterWithInvitationRequest request, String ipAddress, String userAgent) {
        InvitationValidationResponse validation = invitationValidationService.validateInvitation(request.invitationToken());

        if (validation == null || !Boolean.TRUE.equals(validation.valid())) {
            String msg = validation == null ? "Ошибка валидации приглашения"
                    : (validation.message() != null ? validation.message() : "Приглашение недействительно");
            throw AppException.badRequest(msg);
        }

        if (validation.email() != null && !validation.email().equalsIgnoreCase(request.email())) {
            throw AppException.badRequest("Email не совпадает с email в приглашении");
        }

        if (readModelRepository.existsByEmail(request.email())) {
            throw AppException.conflict("Пользователь с таким email уже существует");
        }

        UserRole role;
        try {
            role = UserRole.valueOf(validation.role());
        } catch (IllegalArgumentException e) {
            throw AppException.badRequest("Некорректная роль в приглашении: " + validation.role());
        }
        if (role == UserRole.DIRECTOR) {
            throw AppException.badRequest("Роль DIRECTOR не может быть назначена через приглашение");
        }

        UUID userId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(request.password());

        UserEvents.UserCreatedEvent event = new UserEvents.UserCreatedEvent(
                request.email(),
                request.getFullName(),
                role,
                passwordHash,
                AuthProvider.LOCAL,
                validation.organizationId(),
                validation.warehouseId()
        );

        UserEvent userEvent = UserEvent.builder()
                .userId(userId)
                .eventType("USER_CREATED_INVITATION")
                .eventData(objectMapper.valueToTree(event))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(userEvent);

        UserReadModel readModel = UserReadModel.builder()
                .userId(userId)
                .email(request.email())
                .fullName(request.getFullName())
                .role(role)
                .passwordHash(passwordHash)
                .provider(AuthProvider.LOCAL)
                .organizationId(validation.organizationId())
                .warehouseId(validation.warehouseId())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        readModelRepository.save(readModel);

        invitationValidationService.addEmployeeToOrganization(
                validation.organizationId(), userId, role.name());

        invitationValidationService.markInvitationAsUsed(request.invitationToken(), userId);

        log.info("User registered via invitation: {} (role={}, orgId={}, warehouseId={})",
                request.email(), role, validation.organizationId(), validation.warehouseId());

        return generateTokensWithAudit(readModel, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        UserReadModel user = readModelRepository.findByEmail(request.email())
                .orElseThrow(() -> AppException.unauthorized("Неверный email или пароль"));

        if (!user.getIsActive()) {
            throw AppException.forbidden("Аккаунт деактивирован");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw AppException.unauthorized("Неверный email или пароль");
        }

        return generateTokensWithAudit(user, ipAddress, userAgent);
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String refreshToken) {
        UUID userId = refreshTokenService.getUserIdByRefreshToken(refreshToken);

        if (userId == null) {
            throw AppException.unauthorized("Недействительный refresh token");
        }

        UserReadModel user = readModelRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("Пользователь не найден"));

        if (!user.getIsActive()) {
            throw AppException.forbidden("Аккаунт деактивирован");
        }

        refreshTokenService.deleteRefreshToken(refreshToken);

        return generateTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                throw AppException.badRequest("Refresh token обязателен");
            }

            UUID userId = refreshTokenService.getUserIdByRefreshToken(refreshToken);
            if (userId == null) {
                log.warn("Refresh token not found in Redis, trying to deactivate session anyway");

            } else {
                List<LoginAudit> activeSessions = loginAuditRepository.findByUserIdAndIsActiveTrue(userId);

                for (LoginAudit session : activeSessions) {
                    if (session.getRefreshTokenHash() != null &&
                        passwordEncoder.matches(refreshToken, session.getRefreshTokenHash())) {
                        session.setIsActive(false);
                        session.setLogoutAt(LocalDateTime.now());
                        loginAuditRepository.save(session);
                        log.info("Session deactivated for user: {}", userId);
                        break;
                    }
                }
            }

            refreshTokenService.deleteRefreshToken(refreshToken);
            log.info("User logged out successfully");

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Logout error: {}", e.getMessage(), e);
            throw AppException.internalError("Ошибка при выходе из системы");
        }
    }

    @Transactional
    public void logoutAll(UUID userId) {
        loginAuditRepository.deactivateAllUserSessions(userId);
        refreshTokenService.deleteAllUserTokens(userId);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserInfo(UUID userId) {
        UserReadModel user = readModelRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("Пользователь не найден"));

        String photoBase64 = null;
        if (user.getPhoto() != null) {
            photoBase64 = Base64.getEncoder().encodeToString(user.getPhoto());
        }

        return new UserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                photoBase64,
                user.getOrganizationId(),
                user.getWarehouseId()
        );
    }

    private AuthResponse generateTokens(UserReadModel user) {
        UserRole role = user.getRole();

        String accessToken = jwtTokenService.generateAccessToken(
                user.getUserId(),
                user.getEmail(),
                role,
                user.getOrganizationId(),
                user.getWarehouseId()
        );

        String refreshToken = jwtTokenService.generateRefreshToken();

        refreshTokenService.saveRefreshToken(
                refreshToken,
                user.getUserId(),
                Duration.ofSeconds(jwtTokenService.getRefreshTokenValidity())
        );

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtTokenService.getAccessTokenValidity()
        );
    }

    public AuthResponse generateTokensWithAudit(UserReadModel user, String ipAddress, String userAgent) {
        UserRole role = user.getRole();

        String accessToken = jwtTokenService.generateAccessToken(
                user.getUserId(),
                user.getEmail(),
                role,
                user.getOrganizationId(),
                user.getWarehouseId()
        );

        String refreshToken = jwtTokenService.generateRefreshToken();

        refreshTokenService.saveRefreshToken(
                refreshToken,
                user.getUserId(),
                Duration.ofSeconds(jwtTokenService.getRefreshTokenValidity())
        );

        String refreshTokenHash = passwordEncoder.encode(refreshToken);

        LoginAudit loginAudit = LoginAudit.builder()
                .userId(user.getUserId())
                .refreshTokenHash(refreshTokenHash)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .provider(user.getProvider())
                .loginAt(LocalDateTime.now())
                .isActive(true)
                .build();
        loginAuditRepository.save(loginAudit);

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtTokenService.getAccessTokenValidity()
        );
    }

    @Transactional
    public AuthResponse loginOAuthUser(UserReadModel user, String ipAddress, String userAgent) {
        if (!user.getIsActive()) {
            throw AppException.forbidden("Аккаунт деактивирован");
        }

        log.info("OAuth user logged in: {}", user.getEmail());
        return generateTokensWithAudit(user, ipAddress, userAgent);
    }

    @Transactional
    public UserReadModel createOAuthUser(
            String email,
            String fullName,
            String provider,
            byte[] photo,
            UserRole role,
            String organizationCode,
            String warehouseCode) {

        if (readModelRepository.existsByEmail(email)) {
            throw AppException.conflict("Пользователь с таким email уже существует");
        }

        UUID userId = UUID.randomUUID();
        UUID organizationId = null;
        UUID warehouseId = null;

        if (organizationCode != null && !organizationCode.isBlank()) {
            organizationId = UUID.fromString(organizationCode);
        }

        if (warehouseCode != null && !warehouseCode.isBlank()) {
            warehouseId = UUID.fromString(warehouseCode);
        }

        UserEvents.UserCreatedEvent event = new UserEvents.UserCreatedEvent(
                email,
                fullName,
                role,
                null,
                AuthProvider.valueOf(provider.toUpperCase()),
                organizationId,
                warehouseId
        );

        UserEvent userEvent = UserEvent.builder()
                .userId(userId)
                .eventType("USER_CREATED_OAUTH")
                .eventData(objectMapper.valueToTree(event))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(userEvent);

        UserReadModel readModel = UserReadModel.builder()
                .userId(userId)
                .email(email)
                .fullName(fullName)
                .role(role)
                .passwordHash(null)
                .provider(AuthProvider.valueOf(provider.toUpperCase()))
                .photo(photo)
                .organizationId(organizationId)
                .warehouseId(warehouseId)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        readModelRepository.save(readModel);
        log.info("OAuth user created: {} via {}", email, provider);

        return readModel;
    }
}
