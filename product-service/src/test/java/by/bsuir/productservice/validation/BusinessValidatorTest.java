package by.bsuir.productservice.validation;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BusinessValidator — модульные тесты")
class BusinessValidatorTest {

    @Mock private InventoryRepository inventoryRepository;

    @InjectMocks private BusinessValidator validator;

    private Inventory inv(BigDecimal qty, BigDecimal reserved) {
        return Inventory.builder().quantity(qty).reservedQuantity(reserved).build();
    }

    @Test
    @DisplayName("validateInventoryAvailability: доступно >= запрос → ОК")
    void inventoryAvailability_GivenEnough_ShouldNotThrow() {
        UUID p = UUID.randomUUID(), w = UUID.randomUUID();
        when(inventoryRepository.findByProductIdAndWarehouseIdAndQuantityGreaterThan(p, w, BigDecimal.ZERO))
                .thenReturn(List.of(inv(new BigDecimal("100"), BigDecimal.ZERO)));

        assertThatCode(() -> validator.validateInventoryAvailability(p, w, new BigDecimal("50")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateInventoryAvailability: запрос > доступно → bad request")
    void inventoryAvailability_GivenNotEnough_ShouldThrow() {
        UUID p = UUID.randomUUID(), w = UUID.randomUUID();
        when(inventoryRepository.findByProductIdAndWarehouseIdAndQuantityGreaterThan(p, w, BigDecimal.ZERO))
                .thenReturn(List.of(inv(new BigDecimal("10"), BigDecimal.ZERO)));

        assertThatThrownBy(() -> validator.validateInventoryAvailability(p, w, new BigDecimal("50")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Недостаточно");
    }

    @Test
    @DisplayName("validateReservationAvailability: учитывает уже зарезервированные")
    void reservationAvailability_GivenReservation_ShouldSubtract() {
        UUID p = UUID.randomUUID(), w = UUID.randomUUID();
        when(inventoryRepository.findByProductIdAndWarehouseIdAndQuantityGreaterThan(p, w, BigDecimal.ZERO))
                .thenReturn(List.of(inv(new BigDecimal("100"), new BigDecimal("80"))));

        assertThatThrownBy(() -> validator.validateReservationAvailability(p, w, new BigDecimal("50")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Недостаточно");
    }

    @Test
    @DisplayName("validateWarehouseCapacity: превышение максимума → bad request")
    void warehouseCapacity_GivenOverflow_ShouldThrow() {
        assertThatThrownBy(() -> validator.validateWarehouseCapacity(UUID.randomUUID(), 90, 20, 100))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Превышена");
    }

    @Test
    @DisplayName("validateWarehouseCapacity: в пределах → ОК (с warning >95%)")
    void warehouseCapacity_GivenNearFull_ShouldWarnButPass() {
        assertThatCode(() -> validator.validateWarehouseCapacity(UUID.randomUUID(), 95, 1, 100))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateBatchExpiry: партия с null expiry → ОК")
    void batchExpiry_GivenNoExpiry_ShouldNotThrow() {
        ProductBatch b = ProductBatch.builder().batchNumber("B-1").expiryDate(null).build();
        assertThatCode(() -> validator.validateBatchExpiry(b)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateBatchExpiry: просрочена → bad request")
    void batchExpiry_GivenExpired_ShouldThrow() {
        ProductBatch b = ProductBatch.builder()
                .batchNumber("B-1").expiryDate(LocalDate.now().minusDays(1)).build();
        assertThatThrownBy(() -> validator.validateBatchExpiry(b))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("просрочена");
    }

    @Test
    @DisplayName("validateBatchExpiry: <=7 дней → ОК (только warning)")
    void batchExpiry_GivenSoonExpiry_ShouldWarnButPass() {
        ProductBatch b = ProductBatch.builder()
                .batchNumber("B-1").expiryDate(LocalDate.now().plusDays(3)).build();
        assertThatCode(() -> validator.validateBatchExpiry(b)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateBatchDates: null входы → ОК")
    void batchDates_GivenNulls_ShouldNotThrow() {
        assertThatCode(() -> validator.validateBatchDates(null, null)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateBatchDates(LocalDate.now(), null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateBatchDates: дата производства в будущем → bad request")
    void batchDates_GivenFutureManufacture_ShouldThrow() {
        assertThatThrownBy(() -> validator.validateBatchDates(
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(10)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("в будущем");
    }

    @Test
    @DisplayName("validateBatchDates: expiry < сегодня → bad request")
    void batchDates_GivenPastExpiry_ShouldThrow() {
        assertThatThrownBy(() -> validator.validateBatchDates(
                LocalDate.now().minusDays(30), LocalDate.now().minusDays(1)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("истек");
    }

    @Test
    @DisplayName("validateBatchDates: manufacture > expiry → bad request")
    void batchDates_GivenManufactureAfterExpiry_ShouldThrow() {
        assertThatThrownBy(() -> validator.validateBatchDates(
                LocalDate.now(), LocalDate.now().minusDays(0).minusYears(1)))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("validateBatchDates: валидные даты → ОК")
    void batchDates_GivenValid_ShouldNotThrow() {
        assertThatCode(() -> validator.validateBatchDates(
                LocalDate.now().minusDays(30), LocalDate.now().plusDays(60)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validatePositiveQuantity: null/0/negative → bad request")
    void positiveQuantity_GivenInvalid_ShouldThrow() {
        assertThatThrownBy(() -> validator.validatePositiveQuantity(null))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> validator.validatePositiveQuantity(BigDecimal.ZERO))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> validator.validatePositiveQuantity(new BigDecimal("-1")))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("validatePositiveQuantity: положительное → ОК")
    void positiveQuantity_GivenPositive_ShouldNotThrow() {
        assertThatCode(() -> validator.validatePositiveQuantity(new BigDecimal("0.001")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validatePositivePrice: null/0/negative → bad request, положительное → ОК")
    void positivePrice_Variants() {
        assertThatThrownBy(() -> validator.validatePositivePrice(null)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> validator.validatePositivePrice(BigDecimal.ZERO)).isInstanceOf(AppException.class);
        assertThatCode(() -> validator.validatePositivePrice(new BigDecimal("0.01"))).doesNotThrowAnyException();
    }
}
