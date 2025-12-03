package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ProductBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductBatchRepository extends JpaRepository<ProductBatch, UUID> {

    List<ProductBatch> findByProductId(UUID productId);

    boolean existsByBatchNumber(String batchNumber);

    List<ProductBatch> findByProductIdOrderByCreatedAtDesc(UUID productId);

    List<ProductBatch> findByExpiryDateBefore(LocalDate date);

    @Query("SELECT pb FROM ProductBatch pb WHERE pb.expiryDate IS NOT NULL AND pb.expiryDate < :date")
    List<ProductBatch> findExpiredBatches(@Param("date") LocalDate date);
}
