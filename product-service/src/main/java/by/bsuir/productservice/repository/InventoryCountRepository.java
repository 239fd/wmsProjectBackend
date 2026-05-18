package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.InventoryCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryCountRepository extends JpaRepository<InventoryCount, UUID> {

    List<InventoryCount> findBySessionId(UUID sessionId);

    List<InventoryCount> findBySessionIdAndDiscrepancyNot(UUID sessionId, java.math.BigDecimal zero);

    List<InventoryCount> findByOrganizationIdAndWarehouseIdAndMarkedForWriteoffTrue(UUID organizationId, UUID warehouseId);

    Page<InventoryCount> findByOrganizationIdAndWarehouseIdAndMarkedForWriteoffTrue(UUID organizationId, UUID warehouseId, Pageable pageable);

    List<InventoryCount> findByOrganizationIdAndMarkedForWriteoffTrue(UUID organizationId);

    Page<InventoryCount> findByOrganizationIdAndMarkedForWriteoffTrue(UUID organizationId, Pageable pageable);
}
