package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ShipmentRequest;
import by.bsuir.productservice.model.enums.ShipmentRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentRequestRepository extends JpaRepository<ShipmentRequest, UUID> {

    List<ShipmentRequest> findByOrganizationId(UUID organizationId);

    Page<ShipmentRequest> findByOrganizationId(UUID organizationId, Pageable pageable);

    List<ShipmentRequest> findByOrganizationIdAndStatus(UUID organizationId, ShipmentRequestStatus status);

    List<ShipmentRequest> findByWarehouseId(UUID warehouseId);
}
