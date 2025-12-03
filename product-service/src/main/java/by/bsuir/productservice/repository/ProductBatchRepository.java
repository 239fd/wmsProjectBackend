package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ProductBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductBatchRepository extends JpaRepository<ProductBatch, UUID> {

    List<ProductBatch> findByProductId(UUID productId);

    boolean existsByBatchNumber(String batchNumber);

    List<ProductBatch> findByProductIdOrderByCreatedAtDesc(UUID productId);
}
