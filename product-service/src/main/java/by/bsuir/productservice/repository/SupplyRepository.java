package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.Supply;
import by.bsuir.productservice.model.enums.SupplyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupplyRepository extends JpaRepository<Supply, UUID> {

    List<Supply> findByWarehouseId(UUID warehouseId);

    List<Supply> findBySupplierId(UUID supplierId);

    List<Supply> findByStatus(SupplyStatus status);

    List<Supply> findByOrganizationId(UUID organizationId);

    List<Supply> findByOrganizationIdAndStatus(UUID organizationId, SupplyStatus status);

    List<Supply> findByWarehouseIdAndStatus(UUID warehouseId, SupplyStatus status);
}