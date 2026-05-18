package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ProductReadModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductReadModelRepository
        extends JpaRepository<ProductReadModel, UUID>, JpaSpecificationExecutor<ProductReadModel> {

    Optional<ProductReadModel> findBySku(String sku);

    Optional<ProductReadModel> findByBarcode(String barcode);

    Optional<ProductReadModel> findByProductIdAndOrganizationId(UUID productId, UUID organizationId);

    List<ProductReadModel> findByCategory(String category);

    Page<ProductReadModel> findByCategory(String category, Pageable pageable);

    boolean existsBySku(String sku);

    boolean existsByBarcode(String barcode);

    @Query(value = "SELECT * FROM product_read_model p WHERE "
            + "LOWER(p.name) LIKE LOWER('%' || :q || '%') "
            + "OR LOWER(COALESCE(p.sku, '')) LIKE LOWER('%' || :q || '%') "
            + "OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER('%' || :q || '%') "
            + "ORDER BY p.name ASC LIMIT :limit",
           nativeQuery = true)
    List<ProductReadModel> searchByTextNative(@Param("q") String query, @Param("limit") int limit);
}
