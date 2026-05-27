package by.bsuir.productservice.service;

import by.bsuir.productservice.client.WarehouseClient;
import by.bsuir.productservice.client.dto.CellInfoDto;
import by.bsuir.productservice.client.dto.RackInfoDto;
import by.bsuir.productservice.dto.request.PlacementRequest;
import by.bsuir.productservice.dto.response.PlacementResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.InventoryStatus;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.model.enums.StorageConditions;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementService {

    private final WarehouseClient warehouseClient;
    private final ProductBatchRepository batchRepository;
    private final ProductReadModelRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductOperationRepository operationRepository;

    public UUID autoSelectCellForReceipt(
            UUID warehouseId, UUID productId, BigDecimal quantity, Integer unitsPerPackage,
            StorageConditions overrideConditions, String userRole) {
        return autoSelectCellForReceipt(warehouseId, productId, quantity, unitsPerPackage,
                overrideConditions, null, null, null, null, null, null, userRole);
    }

    public UUID autoSelectCellForReceipt(
            UUID warehouseId, UUID productId, BigDecimal quantity, Integer unitsPerPackage,
            StorageConditions overrideConditions,
            by.bsuir.productservice.model.enums.PackagingType packagingType,
            String userRole) {
        return autoSelectCellForReceipt(warehouseId, productId, quantity, unitsPerPackage,
                overrideConditions, packagingType, null, null, null, null, null, userRole);
    }

    public UUID autoSelectCellForReceipt(
            UUID warehouseId, UUID productId, BigDecimal quantity, Integer unitsPerPackage,
            StorageConditions overrideConditions,
            by.bsuir.productservice.model.enums.PackagingType packagingType,
            String palletType,
            BigDecimal packageLengthCm, BigDecimal packageWidthCm, BigDecimal packageHeightCm,
            BigDecimal packageWeightKg,
            String userRole) {
        ProductReadModel product = productRepository.findById(productId).orElse(null);
        StorageConditions required = overrideConditions;
        if (required == null && product != null) required = product.getRequiredStorageCondition();
        if (required == null) required = StorageConditions.ROOM;
        final StorageConditions cond = required;

        int upp = (unitsPerPackage != null && unitsPerPackage > 0) ? unitsPerPackage : 1;
        BigDecimal numPackages = quantity != null
                ? quantity.divide(BigDecimal.valueOf(upp), 0, java.math.RoundingMode.CEILING)
                : BigDecimal.ZERO;

        BigDecimal incomingWeightKg = packageWeightKg != null
                ? packageWeightKg.multiply(numPackages)
                : BigDecimal.ZERO;

        boolean requirePallet = packagingType == by.bsuir.productservice.model.enums.PackagingType.PALLET;

        List<RackInfoDto> matching;
        try {
            matching = warehouseClient.getRacksByWarehouse(warehouseId, userRole).stream()
                    .filter(r -> Boolean.TRUE.equals(r.isActive()))
                    .filter(r -> matchesConditions(r.storageConditions(), cond))
                    .filter(r -> requirePallet
                            ? "PALLET".equals(r.kind())
                            : !"PALLET".equals(r.kind()))
                    .toList();
        } catch (Exception e) {
            log.warn("autoSelectCellForReceipt: warehouseClient failed: {}", e.getMessage());
            return null;
        }
        if (matching.isEmpty()) {
            log.warn("autoSelectCellForReceipt: нет стеллажей с условиями {} на складе {}", cond, warehouseId);
            return null;
        }

        List<Inventory> warehouseInv = inventoryRepository.findByWarehouseId(warehouseId);
        Set<UUID> occupied = warehouseInv.stream()
                .filter(inv -> inv.getQuantity() != null && inv.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(Inventory::getCellId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, BigDecimal> weightByRack = computeWeightByRack(warehouseInv);

        for (RackInfoDto rack : matching) {
            BigDecimal rackUsed = weightByRack.getOrDefault(rack.rackId(), BigDecimal.ZERO);
            if (rack.maxWeightKg() != null
                    && rackUsed.add(incomingWeightKg).compareTo(rack.maxWeightKg()) > 0) {
                log.info("autoSelectCellForReceipt: стеллаж {} перегружен — used={}кг, нужно ещё {}кг, max={}кг",
                        rack.name(), rackUsed, incomingWeightKg, rack.maxWeightKg());
                continue;
            }

            List<CellInfoDto> cells;
            try {
                cells = warehouseClient.getCellsByRack(rack.rackId(), userRole);
            } catch (Exception e) {
                continue;
            }
            for (CellInfoDto cell : cells) {
                if (occupied.contains(cell.cellId())) continue;
                if (requirePallet && palletType != null && cell.palletType() != null
                        && !palletType.equalsIgnoreCase(cell.palletType())) {
                    continue;
                }
                BigDecimal effectiveHeight = cell.remainingHeightCm() != null
                        ? cell.remainingHeightCm() : cell.heightCm();
                long cap = capacityByDims(
                        cell.lengthCm(), cell.widthCm(), effectiveHeight,
                        packageLengthCm, packageWidthCm, packageHeightCm);
                if (cap == 0L) {
                    log.debug("Cell {} не вмещает упаковку: package={}x{}x{}см, cell={}x{}x{}см",
                            cell.cellId(),
                            packageLengthCm, packageWidthCm, packageHeightCm,
                            cell.lengthCm(), cell.widthCm(), effectiveHeight);
                    continue;
                }
                log.info("Auto-picked cell {} on rack {} for product {} (weight={}кг, capacity={}шт, need={}шт)",
                        cell.cellId(), rack.name(), productId, incomingWeightKg, cap, numPackages);
                return cell.cellId();
            }
        }
        log.warn("autoSelectCellForReceipt: нет свободных ячеек на складе {} (cond={}, weight={}кг)",
                warehouseId, cond, incomingWeightKg);
        return null;
    }

    public record PalletSelection(List<UUID> placeIds, String shortageReason) {
        public boolean enough(long required) {
            return placeIds.size() >= required;
        }
    }

    public PalletSelection autoSelectPalletPlaces(
            UUID warehouseId, long numPallets, String palletType,
            StorageConditions overrideConditions,
            BigDecimal loadHeightCm, BigDecimal palletWeightKg, String userRole) {

        final StorageConditions cond = overrideConditions != null ? overrideConditions : StorageConditions.ROOM;

        List<RackInfoDto> matching;
        try {
            matching = warehouseClient.getRacksByWarehouse(warehouseId, userRole).stream()
                    .filter(r -> Boolean.TRUE.equals(r.isActive()))
                    .filter(r -> "PALLET".equals(r.kind()))
                    .filter(r -> matchesConditions(r.storageConditions(), cond))
                    .toList();
        } catch (Exception e) {
            log.warn("autoSelectPalletPlaces: warehouseClient failed: {}", e.getMessage());
            return new PalletSelection(List.of(),
                    "не удалось получить список стеллажей склада — попробуйте позже");
        }
        if (matching.isEmpty()) {
            log.warn("autoSelectPalletPlaces: нет паллет-стеллажей с условиями {} на складе {}", cond, warehouseId);
            return new PalletSelection(List.of(),
                    "на складе нет паллетных стеллажей с условиями «" + cond.getLabel() + "»");
        }

        List<Inventory> warehouseInv = inventoryRepository.findByWarehouseId(warehouseId);
        Set<UUID> occupied = warehouseInv.stream()
                .filter(inv -> inv.getQuantity() != null && inv.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(Inventory::getCellId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, BigDecimal> weightByRack = computeWeightByRack(warehouseInv);
        BigDecimal weightPerPallet = palletWeightKg != null ? palletWeightKg : BigDecimal.ZERO;

        List<UUID> selected = new java.util.ArrayList<>();
        Set<UUID> chosen = new java.util.HashSet<>();
        Map<UUID, BigDecimal> rackRunningWeight = new java.util.HashMap<>(weightByRack);

        int freeTotal = 0;
        int blockedType = 0;
        int blockedHeight = 0;
        int blockedWeight = 0;
        BigDecimal sampleMaxHeight = null;
        BigDecimal sampleRackLimit = null;

        for (RackInfoDto rack : matching) {
            List<CellInfoDto> cells;
            try {
                cells = warehouseClient.getCellsByRack(rack.rackId(), userRole);
            } catch (Exception e) {
                continue;
            }
            for (CellInfoDto cell : cells) {
                if (occupied.contains(cell.cellId()) || chosen.contains(cell.cellId())) continue;
                freeTotal++;

                if (palletType != null && cell.palletType() != null
                        && !palletType.equalsIgnoreCase(cell.palletType())) {
                    blockedType++;
                    continue;
                }

                BigDecimal placeMaxHeight = cell.maxHeightCm() != null ? cell.maxHeightCm() : cell.heightCm();
                if (loadHeightCm != null && placeMaxHeight != null
                        && loadHeightCm.compareTo(placeMaxHeight) > 0) {
                    log.debug("autoSelectPalletPlaces: место {} ниже груза — груз {}см, место {}см",
                            cell.cellId(), loadHeightCm, placeMaxHeight);
                    blockedHeight++;
                    sampleMaxHeight = placeMaxHeight;
                    continue;
                }

                BigDecimal rackUsed = rackRunningWeight.getOrDefault(rack.rackId(), BigDecimal.ZERO);
                if (rack.maxWeightKg() != null
                        && rackUsed.add(weightPerPallet).compareTo(rack.maxWeightKg()) > 0) {
                    log.info("autoSelectPalletPlaces: стеллаж {} перегружен — занято {}кг, паллет {}кг, лимит {}кг",
                            rack.name(), rackUsed, weightPerPallet, rack.maxWeightKg());
                    blockedWeight++;
                    sampleRackLimit = rack.maxWeightKg();
                    continue;
                }

                selected.add(cell.cellId());
                chosen.add(cell.cellId());
                rackRunningWeight.put(rack.rackId(), rackUsed.add(weightPerPallet));
                if (selected.size() >= numPallets) break;
            }
            if (selected.size() >= numPallets) break;
        }

        log.info("autoSelectPalletPlaces: подобрано {}/{} паллет-мест на складе {} (тип={}, условия={})",
                selected.size(), numPallets, warehouseId, palletType, cond);

        String reason = null;
        if (selected.size() < numPallets) {
            reason = buildPalletShortageReason(
                    numPallets, selected.size(), freeTotal,
                    blockedWeight, blockedHeight, blockedType,
                    palletType, weightPerPallet, loadHeightCm, sampleRackLimit, sampleMaxHeight);
        }
        return new PalletSelection(selected, reason);
    }

    private String buildPalletShortageReason(
            long need, int found, int freeTotal,
            int blockedWeight, int blockedHeight, int blockedType,
            String palletType, BigDecimal weightPerPallet, BigDecimal loadHeightCm,
            BigDecimal sampleRackLimit, BigDecimal sampleMaxHeight) {
        String plain = plain(weightPerPallet);
        if (freeTotal == 0) {
            return "все паллет-места заняты — нет ни одного свободного";
        }
        if (blockedWeight >= blockedHeight && blockedWeight >= blockedType && blockedWeight > 0) {
            return "вес паллета " + plain + " кг превышает свободную грузоподъёмность стеллажа"
                    + (sampleRackLimit != null ? " (лимит " + plain(sampleRackLimit) + " кг)" : "");
        }
        if (blockedHeight >= blockedType && blockedHeight > 0) {
            return "высота груза " + plain(loadHeightCm) + " см превышает максимальную высоту мест"
                    + (sampleMaxHeight != null ? " (" + plain(sampleMaxHeight) + " см)" : "");
        }
        if (blockedType > 0) {
            return "нет свободных паллет-мест под тип паллета " + palletType;
        }
        return "свободно лишь " + found + " подходящих паллет-мест из требуемых " + need;
    }

    private static String plain(BigDecimal v) {
        return v == null ? "—" : v.stripTrailingZeros().toPlainString();
    }


    private boolean fitsByLinearDimensions(
            BigDecimal pL, BigDecimal pW, BigDecimal pH,
            BigDecimal cL, BigDecimal cW, BigDecimal cH) {
        if (pL == null || pW == null || pH == null) return true;
        if (cL == null || cW == null || cH == null) return true;
        BigDecimal[] pkg = { pL, pW, pH };
        BigDecimal[] cel = { cL, cW, cH };
        java.util.Arrays.sort(pkg);
        java.util.Arrays.sort(cel);
        return pkg[0].compareTo(cel[0]) <= 0
                && pkg[1].compareTo(cel[1]) <= 0
                && pkg[2].compareTo(cel[2]) <= 0;
    }

    private long capacityByDims(
            BigDecimal cL, BigDecimal cW, BigDecimal cH,
            BigDecimal pL, BigDecimal pW, BigDecimal pH) {
        if (pL == null || pW == null || pH == null) return -1L;
        if (cL == null || cW == null || cH == null) return -1L;
        if (pL.signum() <= 0 || pW.signum() <= 0 || pH.signum() <= 0) return -1L;
        if (cL.signum() <= 0 || cW.signum() <= 0 || cH.signum() <= 0) return -1L;
        BigDecimal[] pkg = { pL, pW, pH };
        BigDecimal[] cel = { cL, cW, cH };
        java.util.Arrays.sort(pkg);
        java.util.Arrays.sort(cel);
        if (pkg[0].compareTo(cel[0]) > 0 || pkg[1].compareTo(cel[1]) > 0 || pkg[2].compareTo(cel[2]) > 0) {
            return 0L;
        }
        long c0 = cel[0].divide(pkg[0], 0, java.math.RoundingMode.FLOOR).longValueExact();
        long c1 = cel[1].divide(pkg[1], 0, java.math.RoundingMode.FLOOR).longValueExact();
        long c2 = cel[2].divide(pkg[2], 0, java.math.RoundingMode.FLOOR).longValueExact();
        return c0 * c1 * c2;
    }

    private void enforcePlacementFit(
            CellInfoDto cell, RackInfoDto rack, ProductBatch batch, BigDecimal quantityUnits,
            BigDecimal rackUsedKg, boolean isPallet) {
        enforcePlacementFitRaw(
                cell, rack,
                batch.getPackageLengthCm(), batch.getPackageWidthCm(), batch.getPackageHeightCm(),
                batch.getPackageWeightKg(), batch.getUnitsPerPackage(),
                quantityUnits, rackUsedKg, isPallet);
    }

    private void enforcePlacementFitRaw(
            CellInfoDto cell, RackInfoDto rack,
            BigDecimal pL, BigDecimal pW, BigDecimal pH, BigDecimal pWeight, Integer unitsPerPackage,
            BigDecimal quantityUnits, BigDecimal rackUsedKg, boolean isPallet) {

        int uppEff = (unitsPerPackage != null && unitsPerPackage > 0) ? unitsPerPackage : 1;
        BigDecimal numPackages = quantityUnits != null
                ? quantityUnits.divide(BigDecimal.valueOf(uppEff), 0, java.math.RoundingMode.CEILING)
                : BigDecimal.ZERO;
        BigDecimal totalWeightKg = pWeight != null
                ? pWeight.multiply(numPackages)
                : BigDecimal.ZERO;

        BigDecimal effectiveHeight = cell.remainingHeightCm() != null
                ? cell.remainingHeightCm() : cell.heightCm();
        long cap = capacityByDims(cell.lengthCm(), cell.widthCm(), effectiveHeight, pL, pW, pH);
        if (cap == 0L) {
            throw AppException.conflict(
                    "Размеры упаковки (" + pL + "×" + pW + "×" + pH + "см) превышают доступные размеры места ("
                            + cell.lengthCm() + "×" + cell.widthCm() + "×" + effectiveHeight
                            + "см осталось)");
        }
        if (rack != null && rack.maxWeightKg() != null
                && rackUsedKg != null
                && rackUsedKg.add(totalWeightKg).compareTo(rack.maxWeightKg()) > 0) {
            throw AppException.conflict(
                    "Стеллаж перегружен: занято " + rackUsedKg + "кг, нужно ещё " + totalWeightKg
                            + "кг, лимит " + rack.maxWeightKg() + "кг");
        }
    }

    public void validateReceiptCellFit(
            UUID warehouseId, UUID cellId,
            BigDecimal packageLengthCm, BigDecimal packageWidthCm, BigDecimal packageHeightCm,
            BigDecimal packageWeightKg, Integer unitsPerPackage, BigDecimal quantityUnits,
            by.bsuir.productservice.model.enums.PackagingType packagingType,
            String userRole) {
        if (cellId == null) return;

        UUID rackId = lookupRackOfCell(cellId);
        if (rackId == null) return;

        RackInfoDto rack = null;
        try {
            rack = warehouseClient.getRack(rackId, userRole);
        } catch (Exception ignored) { }
        if (rack == null) return;

        if (warehouseId != null && rack.warehouseId() != null
                && !warehouseId.equals(rack.warehouseId())) {
            throw AppException.conflict(
                    "Выбранная ячейка принадлежит другому складу — выберите ячейку текущего склада");
        }

        CellInfoDto cell = resolveCellInfo(cellId, rackId, userRole);
        if (cell == null) return;

        boolean rackIsPallet = "PALLET".equals(rack.kind());
        boolean batchIsPallet =
                packagingType == by.bsuir.productservice.model.enums.PackagingType.PALLET;
        if (rackIsPallet != batchIsPallet) {
            throw AppException.conflict(batchIsPallet
                    ? "Упаковка PALLET — выберите паллет-место, а не ячейку/полку"
                    : "Упаковка " + packagingType + " — выберите ячейку/полку, а не паллет-место");
        }

        Map<UUID, BigDecimal> rackWeights = computeWeightByRack(
                inventoryRepository.findByWarehouseId(warehouseId));
        BigDecimal rackUsed = rackWeights.getOrDefault(rackId, BigDecimal.ZERO);

        enforcePlacementFitRaw(
                cell, rack,
                packageLengthCm, packageWidthCm, packageHeightCm,
                packageWeightKg, unitsPerPackage,
                quantityUnits, rackUsed, batchIsPallet);
    }

    private CellInfoDto resolveCellInfo(UUID cellId, UUID rackId, String userRole) {
        try {
            return warehouseClient.getCellsByRack(rackId, userRole).stream()
                    .filter(c -> cellId.equals(c.cellId()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<UUID, BigDecimal> computeWeightByRack(List<Inventory> warehouseInv) {
        Map<UUID, BigDecimal> byRack = new java.util.HashMap<>();
        for (Inventory inv : warehouseInv) {
            if (inv.getQuantity() == null || inv.getBatchId() == null || inv.getCellId() == null) continue;
            ProductBatch b = batchRepository.findById(inv.getBatchId()).orElse(null);
            if (b == null || b.getPackageWeightKg() == null) continue;
            int upp = (b.getUnitsPerPackage() != null && b.getUnitsPerPackage() > 0)
                    ? b.getUnitsPerPackage() : 1;
            BigDecimal numPackages = inv.getQuantity()
                    .divide(BigDecimal.valueOf(upp), 0, java.math.RoundingMode.CEILING);
            BigDecimal w = b.getPackageWeightKg().multiply(numPackages);
            UUID rackId = lookupRackOfCell(inv.getCellId());
            if (rackId == null) continue;
            byRack.merge(rackId, w, BigDecimal::add);
        }
        return byRack;
    }

    private final Map<UUID, UUID> cellToRackCache = new java.util.concurrent.ConcurrentHashMap<>();

    private UUID lookupRackOfCell(UUID cellId) {
        if (cellId == null) return null;
        return cellToRackCache.computeIfAbsent(cellId, id -> {
            try {
                Map<String, Object> info = warehouseClient.getCellInfo(id, "WORKER");
                if (info == null) return null;
                Object rackId = info.get("rackId");
                return rackId == null ? null : UUID.fromString(rackId.toString());
            } catch (Exception e) {
                return null;
            }
        });
    }

    @Transactional
    public PlacementResponse autoPlacement(PlacementRequest request, UUID organizationId, String userRole) {
        log.info("Auto placement: batch={} warehouse={} qty={} (org={})",
                request.batchId(), request.warehouseId(), request.quantity(), organizationId);

        ProductBatch batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> AppException.notFound("Партия не найдена"));
        if (organizationId != null && batch.getOrganizationId() != null
                && !organizationId.equals(batch.getOrganizationId())) {
            throw AppException.forbidden("Партия принадлежит другой организации");
        }

        ProductReadModel product = productRepository.findById(batch.getProductId())
                .orElseThrow(() -> AppException.notFound("Товар не найден"));

        StorageConditions resolved = batch.getStorageConditions();
        if (resolved == null) {
            resolved = product.getRequiredStorageCondition();
        }
        if (resolved == null) {
            resolved = StorageConditions.ROOM;
        }
        final StorageConditions required = resolved;

        boolean batchIsPallet = batch.getPackagingType()
                == by.bsuir.productservice.model.enums.PackagingType.PALLET;

        List<RackInfoDto> matchingRacks = warehouseClient.getRacksByWarehouse(request.warehouseId(), userRole)
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.isActive()))
                .filter(r -> matchesConditions(r.storageConditions(), required))
                .filter(r -> batchIsPallet
                        ? "PALLET".equals(r.kind())
                        : !"PALLET".equals(r.kind()))
                .toList();

        if (matchingRacks.isEmpty()) {
            throw AppException.conflict(
                    "На складе нет стеллажей с условиями хранения " + required
                            + " типа " + (batchIsPallet ? "PALLET" : "CELL/SHELF"));
        }

        List<Inventory> warehouseInv = inventoryRepository.findByWarehouseId(request.warehouseId());
        Set<UUID> occupiedCells = warehouseInv.stream()
                .filter(inv -> inv.getQuantity() != null && inv.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(Inventory::getCellId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, BigDecimal> rackWeights = computeWeightByRack(warehouseInv);

        record RackedCell(RackInfoDto rack, CellInfoDto cell, int rackIdx, int cellIdx) {}
        List<RackedCell> freeCells = new java.util.ArrayList<>();
        int rackIdx = 0;
        for (RackInfoDto rack : matchingRacks) {
            List<CellInfoDto> cells = warehouseClient.getCellsByRack(rack.rackId(), userRole);
            int cellIdx = 0;
            BigDecimal rackUsed = rackWeights.getOrDefault(rack.rackId(), BigDecimal.ZERO);
            for (CellInfoDto cell : cells) {
                if (!occupiedCells.contains(cell.cellId())) {
                    try {
                        enforcePlacementFit(cell, rack, batch, request.quantity(), rackUsed, batchIsPallet);
                        freeCells.add(new RackedCell(rack, cell, rackIdx, cellIdx));
                    } catch (AppException skip) {
                        log.debug("Auto: пропущена ячейка {}: {}", cell.cellId(), skip.getMessage());
                    }
                }
                cellIdx++;
            }
            rackIdx++;
        }

        if (freeCells.isEmpty()) {
            throw AppException.conflict("На складе нет подходящих ячеек: проверьте габариты упаковки и вес");
        }

        String abc = product.getAbcClass() != null ? product.getAbcClass() : "B";
        Comparator<RackedCell> sorter = switch (abc) {
            case "A" -> Comparator.comparingInt((RackedCell r) -> r.rackIdx).thenComparingInt(r -> r.cellIdx);
            case "C" -> Comparator.comparingInt((RackedCell r) -> -r.rackIdx).thenComparingInt(r -> -r.cellIdx);
            default -> Comparator.comparingInt((RackedCell r) -> Math.abs(r.rackIdx - matchingRacks.size() / 2));
        };
        freeCells.sort(sorter);

        RackedCell chosen = freeCells.get(0);
        log.info("Auto-selected cell {} (rack {}, ABC class {})",
                chosen.cell.cellId(), chosen.rack.name(), abc);

        return performPlacement(request, batch, organizationId, chosen.cell.cellId(),
                chosen.rack.rackId(), chosen.rack.name(), chosen.rack.storageConditions(), "AUTO");
    }

    @Transactional
    public PlacementResponse manualPlacement(PlacementRequest request, UUID organizationId, String userRole) {
        log.info("Manual placement: batch={} warehouse={} cell={} (org={})",
                request.batchId(), request.warehouseId(), request.cellId(), organizationId);

        if (request.cellId() == null) {
            throw AppException.badRequest("Ячейка обязательна для ручного размещения");
        }

        ProductBatch batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> AppException.notFound("Партия не найдена"));
        if (organizationId != null && batch.getOrganizationId() != null
                && !organizationId.equals(batch.getOrganizationId())) {
            throw AppException.forbidden("Партия принадлежит другой организации");
        }

        ProductReadModel product = productRepository.findById(batch.getProductId())
                .orElseThrow(() -> AppException.notFound("Товар не найден"));

        StorageConditions required = batch.getStorageConditions();
        if (required == null) {
            required = product.getRequiredStorageCondition();
        }
        if (required == null) {
            required = StorageConditions.ROOM;
        }

        List<RackInfoDto> racks = warehouseClient.getRacksByWarehouse(request.warehouseId(), userRole);
        RackInfoDto matchingRack = null;
        for (RackInfoDto rack : racks) {
            List<CellInfoDto> cells = warehouseClient.getCellsByRack(rack.rackId(), userRole);
            if (cells.stream().anyMatch(c -> request.cellId().equals(c.cellId()))) {
                matchingRack = rack;
                break;
            }
        }
        if (matchingRack == null) {
            throw AppException.notFound("Ячейка не найдена на указанном складе");
        }

        if (!matchesConditions(matchingRack.storageConditions(), required)) {
            throw AppException.conflict(
                    "Условия хранения ячейки (" + matchingRack.storageConditions()
                            + ") не соответствуют требуемым (" + required + ")");
        }

        boolean cellOccupied = inventoryRepository.findByCellId(request.cellId())
                .filter(inv -> inv.getQuantity() != null && inv.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .isPresent();
        if (cellOccupied) {
            throw AppException.conflict("Ячейка уже занята");
        }

        CellInfoDto cellInfo = resolveCellInfo(request.cellId(), matchingRack.rackId(), userRole);
        if (cellInfo != null) {
            boolean isPallet = "PALLET".equals(matchingRack.kind());
            boolean packagingPallet = batch.getPackagingType()
                    == by.bsuir.productservice.model.enums.PackagingType.PALLET;
            if (packagingPallet != isPallet) {
                throw AppException.conflict(packagingPallet
                        ? "Упаковка PALLET — выберите паллет-место"
                        : "Упаковка не PALLET — выберите ячейку/полку, а не паллет-место");
            }
            Map<UUID, BigDecimal> rackWeights = computeWeightByRack(
                    inventoryRepository.findByWarehouseId(request.warehouseId()));
            BigDecimal rackUsed = rackWeights.getOrDefault(matchingRack.rackId(), BigDecimal.ZERO);
            enforcePlacementFit(cellInfo, matchingRack, batch, request.quantity(), rackUsed, isPallet);
        }

        return performPlacement(request, batch, organizationId, request.cellId(),
                matchingRack.rackId(), matchingRack.name(), matchingRack.storageConditions(), "MANUAL");
    }

    private PlacementResponse performPlacement(PlacementRequest request, ProductBatch batch,
                                               UUID organizationId, UUID cellId, UUID rackId,
                                               String rackName, String storageConditions, String mode) {
        UUID effectiveOrgId = organizationId != null ? organizationId : batch.getOrganizationId();

        Inventory inventory = Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .productId(batch.getProductId())
                .batchId(batch.getBatchId())
                .organizationId(effectiveOrgId)
                .warehouseId(request.warehouseId())
                .cellId(cellId)
                .quantity(request.quantity())
                .reservedQuantity(BigDecimal.ZERO)
                .status(InventoryStatus.AVAILABLE)
                .lastUpdated(LocalDateTime.now())
                .build();
        inventoryRepository.save(inventory);

        ProductOperation operation = ProductOperation.builder()
                .operationId(UUID.randomUUID())
                .operationType(OperationType.STAGING)
                .productId(batch.getProductId())
                .batchId(batch.getBatchId())
                .organizationId(effectiveOrgId)
                .warehouseId(request.warehouseId())
                .toCellId(cellId)
                .quantity(request.quantity())
                .userId(request.userId())
                .operationDate(LocalDateTime.now())
                .notes("Размещение " + mode + ". " + (request.notes() != null ? request.notes() : ""))
                .build();
        operationRepository.save(operation);

        return new PlacementResponse(
                operation.getOperationId(),
                inventory.getInventoryId(),
                cellId,
                rackId,
                rackName,
                storageConditions,
                mode);
    }

    private boolean matchesConditions(String rackConditions, StorageConditions required) {
        StorageConditions need = required != null ? required : StorageConditions.ROOM;
        StorageConditions slot;
        if (rackConditions == null) {
            slot = StorageConditions.ROOM;
        } else {
            try {
                slot = StorageConditions.valueOf(rackConditions);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return coldnessLevel(slot) >= coldnessLevel(need);
    }

    private static int coldnessLevel(StorageConditions c) {
        return switch (c) {
            case ROOM -> 0;
            case COOL -> 1;
            case FRIDGE -> 2;
            case FREEZER -> 3;
        };
    }

    public void validateTransferFit(
            UUID warehouseId, UUID toCellId, ProductBatch batch, ProductReadModel product,
            BigDecimal quantityUnits, String userRole) {
        if (toCellId == null || batch == null) {
            return;
        }
        UUID rackId = lookupRackOfCell(toCellId);
        if (rackId == null) {
            return;
        }
        RackInfoDto rack;
        try {
            rack = warehouseClient.getRack(rackId, userRole);
        } catch (Exception ignored) {
            rack = null;
        }
        if (rack == null) {
            return;
        }

        StorageConditions required = batch.getStorageConditions();
        if (required == null && product != null) {
            required = product.getRequiredStorageCondition();
        }
        if (required == null) {
            required = StorageConditions.ROOM;
        }
        if (!matchesConditions(rack.storageConditions(), required)) {
            throw AppException.conflict(
                    "Условия хранения ячейки (" + rack.storageConditions()
                            + ") не подходят для товара (требуется " + required + " или строже)");
        }

        validateReceiptCellFit(
                warehouseId, toCellId,
                batch.getPackageLengthCm(), batch.getPackageWidthCm(), batch.getPackageHeightCm(),
                batch.getPackageWeightKg(), batch.getUnitsPerPackage(), quantityUnits,
                batch.getPackagingType(), userRole);
    }
}
