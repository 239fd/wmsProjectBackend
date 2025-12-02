package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    List<Inventory> findByWarehouseId(UUID warehouseId);

    List<Inventory> findByProductId(UUID productId);

    Optional<Inventory> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

    Optional<Inventory> findByCellId(UUID cellId);
}
