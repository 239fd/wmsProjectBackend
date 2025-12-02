package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.PalletPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PalletPlaceRepository extends JpaRepository<PalletPlace, UUID> {

    List<PalletPlace> findByRackId(UUID rackId);

    void deleteByRackId(UUID rackId);
}
