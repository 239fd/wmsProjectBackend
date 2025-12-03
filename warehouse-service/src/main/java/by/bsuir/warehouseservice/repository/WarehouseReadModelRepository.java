package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.WarehouseReadModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseReadModelRepository extends JpaRepository<WarehouseReadModel, UUID> {

    Optional<WarehouseReadModel> findByWarehouseId(UUID warehouseId);

    List<WarehouseReadModel> findByOrgId(UUID orgId);

    List<WarehouseReadModel> findByOrgIdAndIsActiveTrue(UUID orgId);

    boolean existsByOrgIdAndName(UUID orgId, String name);
}
