package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.WarehouseEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WarehouseEventRepository extends JpaRepository<WarehouseEvent, Long> {

    List<WarehouseEvent> findByWarehouseIdOrderByCreatedAtAsc(UUID warehouseId);
}
