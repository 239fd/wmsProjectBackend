package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.Fridge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FridgeRepository extends JpaRepository<Fridge, UUID> {
    List<Fridge> findByRackId(UUID rackId);
}
