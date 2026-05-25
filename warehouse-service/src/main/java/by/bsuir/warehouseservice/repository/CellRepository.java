package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.Cell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CellRepository extends JpaRepository<Cell, UUID> {

    List<Cell> findByRackId(UUID rackId);

    long countByRackId(UUID rackId);

    Optional<Cell> findByWarehouseIdAndSlotCode(UUID warehouseId, String slotCode);

    void deleteByRackId(UUID rackId);

    @Modifying
    @Query("UPDATE Cell c SET c.remainingHeightCm = c.remainingHeightCm + :delta WHERE c.cellId = :id")
    int adjustRemainingHeight(@Param("id") UUID cellId, @Param("delta") BigDecimal delta);
}
