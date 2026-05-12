package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ProductOperationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;



@Repository
public interface ProductOperationEventRepository extends JpaRepository<ProductOperationEvent, Long> {

    List<ProductOperationEvent> findByOperationIdOrderByCreatedAtAsc(UUID operationId);

    @Query("SELECT COALESCE(MAX(e.eventVersion), 0) FROM ProductOperationEvent e WHERE e.operationId = :operationId")
    int findMaxEventVersionByOperationId(UUID operationId);
}