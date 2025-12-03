package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.RackReadModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RackReadModelRepository extends JpaRepository<RackReadModel, UUID> {

    List<RackReadModel> findByWarehouseId(UUID warehouseId);

    List<RackReadModel> findByWarehouseIdAndIsActiveTrue(UUID warehouseId);
}
