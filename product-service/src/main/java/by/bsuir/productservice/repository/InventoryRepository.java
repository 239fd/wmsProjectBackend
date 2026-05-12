package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    List<Inventory> findByWarehouseId(UUID warehouseId);

    List<Inventory> findByProductId(UUID productId);

    Optional<Inventory> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

    Optional<Inventory> findByCellId(UUID cellId);

    List<Inventory> findByProductIdAndWarehouseIdAndQuantityGreaterThan(UUID productId, UUID warehouseId, BigDecimal quantity);

    List<Inventory> findByOrganizationId(UUID organizationId);

    List<Inventory> findByOrganizationIdAndWarehouseId(UUID organizationId, UUID warehouseId);

    List<Inventory> findByOrganizationIdAndProductId(UUID organizationId, UUID productId);

    Optional<Inventory> findByOrganizationIdAndCellId(UUID organizationId, UUID cellId);

    Optional<Inventory> findByUnitSku(String unitSku);

    Optional<Inventory> findByOrganizationIdAndUnitSku(UUID organizationId, String unitSku);
}
