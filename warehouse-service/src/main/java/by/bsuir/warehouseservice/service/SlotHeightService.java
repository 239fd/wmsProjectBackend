package by.bsuir.warehouseservice.service;

import by.bsuir.warehouseservice.exception.AppException;
import by.bsuir.warehouseservice.model.entity.Cell;
import by.bsuir.warehouseservice.model.entity.PalletPlace;
import by.bsuir.warehouseservice.model.entity.Shelf;
import by.bsuir.warehouseservice.repository.CellRepository;
import by.bsuir.warehouseservice.repository.PalletPlaceRepository;
import by.bsuir.warehouseservice.repository.ShelfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlotHeightService {

    private final CellRepository cellRepository;
    private final ShelfRepository shelfRepository;
    private final PalletPlaceRepository palletPlaceRepository;

    @Transactional
    public BigDecimal adjustHeight(UUID slotId, BigDecimal delta) {
        if (slotId == null) {
            throw AppException.badRequest("slotId обязателен");
        }
        if (delta == null || delta.signum() == 0) {
            return readRemainingHeight(slotId);
        }

        Optional<Cell> cell = cellRepository.findById(slotId);
        if (cell.isPresent()) {
            return applyClamped(cell.get().getRemainingHeightCm(), cell.get().getHeightCm(), delta,
                    (clampedDelta) -> cellRepository.adjustRemainingHeight(slotId, clampedDelta),
                    slotId, "cell");
        }
        Optional<Shelf> shelf = shelfRepository.findById(slotId);
        if (shelf.isPresent()) {
            return applyClamped(shelf.get().getRemainingHeightCm(), shelf.get().getHeightCm(), delta,
                    (clampedDelta) -> shelfRepository.adjustRemainingHeight(slotId, clampedDelta),
                    slotId, "shelf");
        }
        Optional<PalletPlace> place = palletPlaceRepository.findById(slotId);
        if (place.isPresent()) {
            BigDecimal max = place.get().getMaxHeightCm() != null
                    ? place.get().getMaxHeightCm()
                    : place.get().getHeightCm();
            return applyClamped(place.get().getRemainingHeightCm(), max, delta,
                    (clampedDelta) -> palletPlaceRepository.adjustRemainingHeight(slotId, clampedDelta),
                    slotId, "pallet-place");
        }
        throw AppException.notFound("Слот не найден: " + slotId);
    }

    private BigDecimal applyClamped(BigDecimal current, BigDecimal capacity, BigDecimal delta,
                                    java.util.function.Function<BigDecimal, Integer> updater,
                                    UUID slotId, String slotType) {
        BigDecimal currentRem = current != null ? current : capacity;
        BigDecimal targetRem = currentRem.add(delta);
        BigDecimal effectiveDelta = delta;
        if (targetRem.signum() < 0) {
            log.warn("{} {} remaining_height clamp: current={}, delta={} → 0",
                    slotType, slotId, currentRem, delta);
            effectiveDelta = currentRem.negate();
            targetRem = BigDecimal.ZERO;
        } else if (capacity != null && targetRem.compareTo(capacity) > 0) {
            log.warn("{} {} remaining_height clamp: current={}, delta={} → capacity={}",
                    slotType, slotId, currentRem, delta, capacity);
            effectiveDelta = capacity.subtract(currentRem);
            targetRem = capacity;
        }
        updater.apply(effectiveDelta);
        return targetRem;
    }

    @Transactional(readOnly = true)
    public BigDecimal readRemainingHeight(UUID slotId) {
        return cellRepository.findById(slotId).map(Cell::getRemainingHeightCm)
                .or(() -> shelfRepository.findById(slotId).map(Shelf::getRemainingHeightCm))
                .or(() -> palletPlaceRepository.findById(slotId).map(PalletPlace::getRemainingHeightCm))
                .orElseThrow(() -> AppException.notFound("Слот не найден: " + slotId));
    }
}
