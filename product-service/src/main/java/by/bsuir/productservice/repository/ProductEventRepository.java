package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ProductEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductEventRepository extends JpaRepository<ProductEvent, Long> {

    List<ProductEvent> findByProductIdOrderByCreatedAtAsc(UUID productId);
}
