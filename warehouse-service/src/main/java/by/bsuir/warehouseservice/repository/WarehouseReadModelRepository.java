package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.WarehouseReadModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseReadModelRepository extends JpaRepository<WarehouseReadModel, UUID> {

    Optional<WarehouseReadModel> findByWarehouseId(UUID warehouseId);

    List<WarehouseReadModel> findByOrgId(UUID orgId);

    Page<WarehouseReadModel> findByOrgId(UUID orgId, Pageable pageable);

    List<WarehouseReadModel> findByOrgIdAndIsActiveTrue(UUID orgId);

    Page<WarehouseReadModel> findByOrgIdAndIsActiveTrue(UUID orgId, Pageable pageable);

    boolean existsByOrgIdAndName(UUID orgId, String name);
}
