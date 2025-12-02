package by.bsuir.warehouseservice.repository;

import by.bsuir.warehouseservice.model.entity.RackEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RackEventRepository extends JpaRepository<RackEvent, Long> {

    List<RackEvent> findByRackIdOrderByCreatedAtAsc(UUID rackId);
}
