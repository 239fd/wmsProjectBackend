package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.Cell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CellRepository extends JpaRepository<Cell, UUID> {

    List<Cell> findByRackId(UUID rackId);

    void deleteByRackId(UUID rackId);
}
