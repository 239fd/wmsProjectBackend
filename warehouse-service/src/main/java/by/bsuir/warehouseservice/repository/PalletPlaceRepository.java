package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.PalletPlace;
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
public interface PalletPlaceRepository extends JpaRepository<PalletPlace, UUID> {

    List<PalletPlace> findByRackId(UUID rackId);

    long countByRackId(UUID rackId);

    Optional<PalletPlace> findByWarehouseIdAndSlotCode(UUID warehouseId, String slotCode);

    void deleteByRackId(UUID rackId);

    @Modifying
    @Query("UPDATE PalletPlace p SET p.remainingHeightCm = p.remainingHeightCm + :delta WHERE p.placeId = :id")
    int adjustRemainingHeight(@Param("id") UUID placeId, @Param("delta") BigDecimal delta);
}
