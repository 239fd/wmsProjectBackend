package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.SupplyItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupplyItemRepository extends JpaRepository<SupplyItem, UUID> {

    List<SupplyItem> findBySupplyId(UUID supplyId);
}