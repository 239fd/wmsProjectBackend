package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.Supply;
import by.bsuir.productservice.model.enums.SupplyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupplyRepository extends JpaRepository<Supply, UUID> {

    List<Supply> findByWarehouseId(UUID warehouseId);

    Page<Supply> findByWarehouseId(UUID warehouseId, Pageable pageable);

    List<Supply> findBySupplierId(UUID supplierId);

    List<Supply> findByStatus(SupplyStatus status);

    Page<Supply> findByStatus(SupplyStatus status, Pageable pageable);

    List<Supply> findByOrganizationId(UUID organizationId);

    Page<Supply> findByOrganizationId(UUID organizationId, Pageable pageable);

    List<Supply> findByOrganizationIdAndStatus(UUID organizationId, SupplyStatus status);

    Page<Supply> findByOrganizationIdAndStatus(UUID organizationId, SupplyStatus status, Pageable pageable);

    List<Supply> findByWarehouseIdAndStatus(UUID warehouseId, SupplyStatus status);
}