package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.InventorySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventorySessionRepository extends JpaRepository<InventorySession, UUID> {

    List<InventorySession> findByWarehouseId(UUID warehouseId);

    List<InventorySession> findByStatus(InventorySession.SessionStatus status);
}
