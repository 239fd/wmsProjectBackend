package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.Shelf;
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
public interface ShelfRepository extends JpaRepository<Shelf, UUID> {

    List<Shelf> findByRackId(UUID rackId);

    long countByRackId(UUID rackId);

    Optional<Shelf> findByWarehouseIdAndSlotCode(UUID warehouseId, String slotCode);

    void deleteByRackId(UUID rackId);

    @Modifying
    @Query("UPDATE Shelf s SET s.remainingHeightCm = s.remainingHeightCm + :delta WHERE s.shelfId = :id")
    int adjustRemainingHeight(@Param("id") UUID shelfId, @Param("delta") BigDecimal delta);
}
