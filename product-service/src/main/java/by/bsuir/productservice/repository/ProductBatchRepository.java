package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ProductBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductBatchRepository extends JpaRepository<ProductBatch, UUID> {

    List<ProductBatch> findByProductId(UUID productId);

    boolean existsByBatchNumber(String batchNumber);

    List<ProductBatch> findByProductIdOrderByCreatedAtDesc(UUID productId);

    Page<ProductBatch> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    List<ProductBatch> findByExpiryDateBefore(LocalDate date);

    @Query("SELECT pb FROM ProductBatch pb WHERE pb.expiryDate IS NOT NULL AND pb.expiryDate < :date")
    List<ProductBatch> findExpiredBatches(@Param("date") LocalDate date);

    List<ProductBatch> findByOrganizationId(UUID organizationId);

    Page<ProductBatch> findByOrganizationId(UUID organizationId, Pageable pageable);

    List<ProductBatch> findByOrganizationIdAndProductId(UUID organizationId, UUID productId);

    Page<ProductBatch> findByOrganizationIdAndProductId(UUID organizationId, UUID productId, Pageable pageable);

    List<ProductBatch> findBySupplyId(UUID supplyId);

    Optional<ProductBatch> findFirstByOrganizationIdAndBatchNumber(UUID organizationId, String batchNumber);
}
