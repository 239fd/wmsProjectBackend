package by.bsuir.ssoservice.controller;

import by.bsuir.ssoservice.exception.AppException;
import by.bsuir.ssoservice.model.entity.UserReadModel;
import by.bsuir.ssoservice.model.enums.UserRole;
import by.bsuir.ssoservice.repository.UserReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalUserController Tests")
class InternalUserControllerTest {

    @Mock
    private UserReadModelRepository userRepository;

    @InjectMocks
    private InternalUserController controller;

    private UserReadModel user(UUID id, UUID orgId, UUID whId) {
        return UserReadModel.builder()
                .userId(id)
                .email("u@u.by")
                .fullName("Иванов И. И.")
                .role(UserRole.WORKER)
                .organizationId(orgId)
                .warehouseId(whId)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("getUser: 200 OK с полным профилем")
    void getUser_givenExistingUser_whenCalled_thenReturnsFullProfile() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID whId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, orgId, whId)));

        ResponseEntity<Map<String, Object>> response = controller.getUser(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("email", "u@u.by")
                .containsEntry("role", "WORKER")
                .containsEntry("organizationId", orgId.toString())
                .containsEntry("warehouseId", whId.toString())
                .containsEntry("isActive", true);
    }

    @Test
    @DisplayName("getUser: организация/склад null → не присутствуют в ответе")
    void getUser_givenNoOrgWarehouse_whenCalled_thenOmitsOptional() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, null, null)));

        ResponseEntity<Map<String, Object>> response = controller.getUser(userId);

        assertThat(response.getBody()).doesNotContainKeys("organizationId", "warehouseId");
    }

    @Test
    @DisplayName("getUser: не найден → notFound")
    void getUser_givenMissing_whenCalled_thenThrows() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getUser(userId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("lookupUsers: пустой список → пустой Map")
    void lookupUsers_givenEmptyIds_whenCalled_thenReturnsEmpty() {
        ResponseEntity<Map<String, Map<String, Object>>> response =
                controller.lookupUsers(Map.of("ids", List.of()));

        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("lookupUsers: набор UUID → Map<userId, profile>")
    void lookupUsers_givenIds_whenCalled_thenReturnsLookupMap() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findAllById(any())).thenReturn(List.of(user(userId, null, null)));

        ResponseEntity<Map<String, Map<String, Object>>> response =
                controller.lookupUsers(Map.of("ids", List.of(userId.toString())));

        assertThat(response.getBody()).containsKey(userId.toString());
        assertThat(response.getBody().get(userId.toString())).containsEntry("email", "u@u.by");
    }

    @Test
    @DisplayName("lookupUsers: пустые строки и null отфильтровываются")
    void lookupUsers_givenBlankIds_whenCalled_thenFiltersBlanks() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findAllById(any())).thenReturn(List.of(user(userId, null, null)));

        // Mix of valid, empty and null
        java.util.List<String> raw = new java.util.ArrayList<>();
        raw.add(userId.toString());
        raw.add("");
        raw.add(null);

        ResponseEntity<Map<String, Map<String, Object>>> response =
                controller.lookupUsers(Map.of("ids", raw));

        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("updateOrganization: обновляет orgId+warehouseId и save")
    void updateOrganization_givenIds_whenCalled_thenUpdatesAndSaves() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID whId = UUID.randomUUID();
        UserReadModel u = user(userId, null, null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, String>> response = controller.updateOrganization(
                userId, Map.of("organizationId", orgId.toString(), "warehouseId", whId.toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(u.getOrganizationId()).isEqualTo(orgId);
        assertThat(u.getWarehouseId()).isEqualTo(whId);
        verify(userRepository).save(u);
    }

    @Test
    @DisplayName("updateOrganization: пустая строка → null (сброс привязки)")
    void updateOrganization_givenBlankStrings_whenCalled_thenSetsNull() {
        UUID userId = UUID.randomUUID();
        UserReadModel u = user(userId, UUID.randomUUID(), UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        controller.updateOrganization(userId, Map.of("organizationId", "", "warehouseId", ""));

        assertThat(u.getOrganizationId()).isNull();
        assertThat(u.getWarehouseId()).isNull();
    }

    @Test
    @DisplayName("updateOrganization: user не найден → notFound")
    void updateOrganization_givenMissingUser_whenCalled_thenThrows() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.updateOrganization(userId, Map.of()))
                .isInstanceOf(AppException.class);
    }
}
