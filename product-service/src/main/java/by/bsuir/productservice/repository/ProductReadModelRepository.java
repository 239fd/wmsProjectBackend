package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ProductReadModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductReadModelRepository extends JpaRepository<ProductReadModel, UUID> {

    Optional<ProductReadModel> findBySku(String sku);

    Optional<ProductReadModel> findByBarcode(String barcode);

    List<ProductReadModel> findByCategory(String category);

    boolean existsBySku(String sku);

    boolean existsByBarcode(String barcode);
}
