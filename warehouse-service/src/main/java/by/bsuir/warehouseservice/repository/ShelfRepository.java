package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.Shelf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShelfRepository extends JpaRepository<Shelf, UUID> {

    List<Shelf> findByRackId(UUID rackId);

    void deleteByRackId(UUID rackId);
}
