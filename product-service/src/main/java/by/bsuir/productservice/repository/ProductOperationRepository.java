package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.enums.OperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductOperationRepository extends JpaRepository<ProductOperation, UUID> {

    List<ProductOperation> findByProductIdOrderByOperationDateDesc(UUID productId);

    List<ProductOperation> findByWarehouseIdOrderByOperationDateDesc(UUID warehouseId);

    List<ProductOperation> findByOperationType(OperationType operationType);

    List<ProductOperation> findByOperationDateBetween(LocalDateTime start, LocalDateTime end);
}
