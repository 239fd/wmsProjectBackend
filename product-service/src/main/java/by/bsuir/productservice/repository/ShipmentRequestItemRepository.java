package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ShipmentRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentRequestItemRepository extends JpaRepository<ShipmentRequestItem, UUID> {

    List<ShipmentRequestItem> findByRequestId(UUID requestId);

    Optional<ShipmentRequestItem> findByRequestIdAndUnitSku(UUID requestId, String unitSku);

    Optional<ShipmentRequestItem> findByRequestIdAndProductIdAndBatchId(UUID requestId, UUID productId, UUID batchId);

    Optional<ShipmentRequestItem> findFirstByRequestIdAndProductIdAndBatchIdIsNull(UUID requestId, UUID productId);
}
