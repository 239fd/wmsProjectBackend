package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    List<Inventory> findByWarehouseId(UUID warehouseId);

    Page<Inventory> findByWarehouseId(UUID warehouseId, Pageable pageable);

    List<Inventory> findByProductId(UUID productId);

    Page<Inventory> findByProductId(UUID productId, Pageable pageable);

    Optional<Inventory> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

    Optional<Inventory> findByCellId(UUID cellId);

    List<Inventory> findByProductIdAndWarehouseIdAndQuantityGreaterThan(UUID productId, UUID warehouseId, BigDecimal quantity);

    List<Inventory> findByOrganizationId(UUID organizationId);

    List<Inventory> findByOrganizationIdAndWarehouseId(UUID organizationId, UUID warehouseId);

    Page<Inventory> findByOrganizationIdAndWarehouseId(UUID organizationId, UUID warehouseId, Pageable pageable);

    List<Inventory> findByOrganizationIdAndProductId(UUID organizationId, UUID productId);

    Page<Inventory> findByOrganizationIdAndProductId(UUID organizationId, UUID productId, Pageable pageable);

    Optional<Inventory> findByOrganizationIdAndCellId(UUID organizationId, UUID cellId);

    Optional<Inventory> findByUnitSku(String unitSku);

    Optional<Inventory> findByOrganizationIdAndUnitSku(UUID organizationId, String unitSku);

    List<Inventory> findByBatchId(UUID batchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId AND i.warehouseId = :warehouseId")
    Optional<Inventory> findByProductIdAndWarehouseIdForUpdate(
            @Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId);

    List<Inventory> findAllByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

    @Query("SELECT i.cellId AS cellId, COUNT(i) AS itemsCount, COALESCE(SUM(i.quantity), 0) AS totalQuantity "
            + "FROM Inventory i WHERE i.cellId IN :cellIds GROUP BY i.cellId")
    List<CellLoadProjection> aggregateLoadByCellIds(@Param("cellIds") List<UUID> cellIds);

    interface CellLoadProjection {
        UUID getCellId();
        Long getItemsCount();
        BigDecimal getTotalQuantity();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.inventoryId = :id")
    Optional<Inventory> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i "
            + "WHERE i.productId = :productId "
            + "AND i.warehouseId = :warehouseId "
            + "AND ((:batchId IS NULL AND i.batchId IS NULL) OR i.batchId = :batchId) "
            + "AND ((:cellId IS NULL AND i.cellId IS NULL) OR i.cellId = :cellId)")
    Optional<Inventory> findExactInventoryForUpdate(
            @Param("productId") UUID productId,
            @Param("batchId") UUID batchId,
            @Param("warehouseId") UUID warehouseId,
            @Param("cellId") UUID cellId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Inventory i WHERE i.quantity <= 0 "
            + "AND (i.reservedQuantity IS NULL OR i.reservedQuantity <= 0)")
    int deleteEmptyInventory();

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Inventory i WHERE i.cellId IS NULL "
            + "AND (i.reservedQuantity IS NULL OR i.reservedQuantity <= 0)")
    int deleteOrphanedInventoryWithoutCell();
}
