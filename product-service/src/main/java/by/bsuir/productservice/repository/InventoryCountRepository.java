package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.InventoryCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryCountRepository extends JpaRepository<InventoryCount, UUID> {

    List<InventoryCount> findBySessionId(UUID sessionId);

    List<InventoryCount> findBySessionIdAndDiscrepancyNot(UUID sessionId, java.math.BigDecimal zero);
}
