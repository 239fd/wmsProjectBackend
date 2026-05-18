package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.ErpConnectionRequest;
import by.bsuir.productservice.dto.response.ErpConnectionResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.ErpConnection;
import by.bsuir.productservice.repository.ErpConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ErpConnectionService Tests")
class ErpConnectionServiceTest {

    @Mock
    private ErpConnectionRepository repository;

    @InjectMocks
    private ErpConnectionService service;

    private UUID orgId;
    private UUID userId;
    private UUID connectionId;
    private ErpConnection entity;
    private ErpConnectionRequest request;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        userId = UUID.randomUUID();
        connectionId = UUID.randomUUID();
        entity = ErpConnection.builder()
                .connectionId(connectionId)
                .organizationId(orgId)
                .aggregator("onec")
                .name("УТ-демо")
                .username("user")
                .password("secret")
                .basePath("File=C:\\\\base")
                .sectionName("Закупки")
                .journalName("Заказы поставщикам")
                .driverUrl("http://127.0.0.1:4723")
                .isDefault(false)
                .createdBy(userId)
                .build();
        request = new ErpConnectionRequest(
                "onec", "УТ-демо", "user", "secret",
                "File=C:\\\\base", "Закупки", "Заказы поставщикам",
                "http://127.0.0.1:4723", false);
    }

    @Test
    @DisplayName("create: успешно сохраняет с createdBy и organizationId")
    void create_givenValidRequest_whenCalled_thenSavesAndReturnsResponse() {
        when(repository.save(any(ErpConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        ErpConnectionResponse response = service.create(request, orgId, userId);

        assertThat(response.organizationId()).isEqualTo(orgId);
        assertThat(response.aggregator()).isEqualTo("onec");
        assertThat(response.hasPassword()).isTrue();
        verify(repository).save(any(ErpConnection.class));
    }

    @Test
    @DisplayName("create: null orgId → badRequest")
    void create_givenNullOrgId_whenCalled_thenThrows() {
        assertThatThrownBy(() -> service.create(request, null, userId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("X-Organization-Id");
    }

    @Test
    @DisplayName("create + isDefault=true: сбрасывает существующие default'ы")
    void create_givenIsDefaultTrue_whenCalled_thenUnsetsOthers() {
        ErpConnectionRequest defaultReq = new ErpConnectionRequest(
                "onec", "Главное", "u", "p", null, null, null, null, true);
        ErpConnection existingDefault = ErpConnection.builder()
                .connectionId(UUID.randomUUID()).organizationId(orgId)
                .aggregator("api").isDefault(true).build();
        when(repository.findByOrganizationIdAndIsDefaultTrue(orgId))
                .thenReturn(List.of(existingDefault));
        when(repository.save(any(ErpConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(defaultReq, orgId, userId);

        assertThat(existingDefault.getIsDefault()).isFalse();
        verify(repository, atLeastOnce()).save(existingDefault);
    }

    @Test
    @DisplayName("list: возвращает все подключения для организации")
    void list_givenOrgId_whenCalled_thenReturnsAllForOrg() {
        when(repository.findByOrganizationIdOrderByCreatedAtDesc(orgId))
                .thenReturn(List.of(entity));

        List<ErpConnectionResponse> list = service.list(orgId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).connectionId()).isEqualTo(connectionId);
    }

    @Test
    @DisplayName("list: null orgId → badRequest")
    void list_givenNullOrgId_whenCalled_thenThrows() {
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("get: существующее подключение возвращается")
    void get_givenExisting_whenCalled_thenReturnsResponse() {
        when(repository.findByConnectionIdAndOrganizationId(connectionId, orgId))
                .thenReturn(Optional.of(entity));

        ErpConnectionResponse response = service.get(connectionId, orgId);

        assertThat(response.aggregator()).isEqualTo("onec");
    }

    @Test
    @DisplayName("get: не найдено → notFound")
    void get_givenMissing_whenCalled_thenThrows() {
        when(repository.findByConnectionIdAndOrganizationId(connectionId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(connectionId, orgId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдено");
    }

    @Test
    @DisplayName("update: обновляет поля и сохраняет, не трогает пароль если null/пустой")
    void update_givenEmptyPassword_whenCalled_thenKeepsOldPassword() {
        ErpConnectionRequest updateReq = new ErpConnectionRequest(
                "api", "Новое", "newuser", "",
                "/api", "X", "Y", null, false);
        when(repository.findByConnectionIdAndOrganizationId(connectionId, orgId))
                .thenReturn(Optional.of(entity));
        when(repository.save(any(ErpConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        ErpConnectionResponse response = service.update(connectionId, updateReq, orgId);

        assertThat(response.aggregator()).isEqualTo("api");
        assertThat(entity.getPassword()).isEqualTo("secret"); // old password kept
        assertThat(entity.getUsername()).isEqualTo("newuser");
    }

    @Test
    @DisplayName("update: пароль не null + не пустой → перезаписывается")
    void update_givenNewPassword_whenCalled_thenOverwritesPassword() {
        ErpConnectionRequest updateReq = new ErpConnectionRequest(
                "onec", "x", "u", "newpass", null, null, null, null, null);
        when(repository.findByConnectionIdAndOrganizationId(connectionId, orgId))
                .thenReturn(Optional.of(entity));
        when(repository.save(any(ErpConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(connectionId, updateReq, orgId);

        assertThat(entity.getPassword()).isEqualTo("newpass");
    }

    @Test
    @DisplayName("update + isDefault=true: сбрасывает другие default'ы и ставит этот")
    void update_givenIsDefaultTrue_whenCalled_thenSetsDefault() {
        ErpConnectionRequest updateReq = new ErpConnectionRequest(
                "onec", "x", "u", "p", null, null, null, null, true);
        when(repository.findByConnectionIdAndOrganizationId(connectionId, orgId))
                .thenReturn(Optional.of(entity));
        when(repository.findByOrganizationIdAndIsDefaultTrue(orgId)).thenReturn(List.of());
        when(repository.save(any(ErpConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(connectionId, updateReq, orgId);

        assertThat(entity.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("update + isDefault=false на default-подключении: сбрасывает default")
    void update_givenIsDefaultFalse_whenCalled_thenUnsetsDefault() {
        entity.setIsDefault(true);
        ErpConnectionRequest updateReq = new ErpConnectionRequest(
                "onec", "x", "u", "p", null, null, null, null, false);
        when(repository.findByConnectionIdAndOrganizationId(connectionId, orgId))
                .thenReturn(Optional.of(entity));
        when(repository.save(any(ErpConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(connectionId, updateReq, orgId);

        assertThat(entity.getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("delete: удаляет owned-подключение")
    void delete_givenExisting_whenCalled_thenDeletes() {
        when(repository.findByConnectionIdAndOrganizationId(connectionId, orgId))
                .thenReturn(Optional.of(entity));

        service.delete(connectionId, orgId);

        verify(repository).delete(entity);
    }

    @Test
    @DisplayName("delete: чужое подключение → notFound (findOwned)")
    void delete_givenMissing_whenCalled_thenThrowsNoDelete() {
        when(repository.findByConnectionIdAndOrganizationId(connectionId, orgId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(connectionId, orgId))
                .isInstanceOf(AppException.class);
        verify(repository, never()).delete(any(ErpConnection.class));
    }

    @Test
    @DisplayName("setDefault: ставит default для текущего и сбрасывает остальные")
    void setDefault_givenExisting_whenCalled_thenMarksAsDefault() {
        ErpConnection otherDefault = ErpConnection.builder()
                .connectionId(UUID.randomUUID()).organizationId(orgId).isDefault(true).build();
        when(repository.findByConnectionIdAndOrganizationId(connectionId, orgId))
                .thenReturn(Optional.of(entity));
        when(repository.findByOrganizationIdAndIsDefaultTrue(orgId))
                .thenReturn(List.of(otherDefault, entity));
        when(repository.save(any(ErpConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setDefault(connectionId, orgId);

        assertThat(entity.getIsDefault()).isTrue();
        assertThat(otherDefault.getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("findDefault(orgId, aggregator): фильтрует по aggregator")
    void findDefault_givenAggregator_whenCalled_thenFiltersByAggregator() {
        when(repository.findFirstByOrganizationIdAndAggregatorAndIsDefaultTrue(orgId, "onec"))
                .thenReturn(Optional.of(entity));

        assertThat(service.findDefault(orgId, "onec")).contains(entity);
    }

    @Test
    @DisplayName("findDefault: null orgId → empty")
    void findDefault_givenNullOrgId_whenCalled_thenReturnsEmpty() {
        assertThat(service.findDefault(null, "onec")).isEmpty();
    }

    @Test
    @DisplayName("findDefault: пустой aggregator → ищет любой default по org")
    void findDefault_givenBlankAggregator_whenCalled_thenIgnoresAggregator() {
        when(repository.findFirstByOrganizationIdAndIsDefaultTrue(orgId))
                .thenReturn(Optional.of(entity));

        assertThat(service.findDefault(orgId, "")).contains(entity);
        assertThat(service.findDefault(orgId, null)).contains(entity);
    }

    @Test
    @DisplayName("findById: null connectionId или orgId → empty")
    void findById_givenNullArg_whenCalled_thenReturnsEmpty() {
        assertThat(service.findById(null, orgId)).isEmpty();
        assertThat(service.findById(connectionId, null)).isEmpty();
    }

    @Test
    @DisplayName("findById: существующее подключение возвращается")
    void findById_givenExisting_whenCalled_thenReturnsEntity() {
        when(repository.findByConnectionIdAndOrganizationId(connectionId, orgId))
                .thenReturn(Optional.of(entity));

        assertThat(service.findById(connectionId, orgId)).contains(entity);
    }
}
