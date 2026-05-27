package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ReceiptSession;
import by.bsuir.productservice.model.enums.ReceiptSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptSessionRepository extends JpaRepository<ReceiptSession, UUID> {

    Optional<ReceiptSession> findBySessionIdAndOrganizationId(UUID sessionId, UUID organizationId);

    Page<ReceiptSession> findByOrganizationIdAndStatus(
            UUID organizationId, ReceiptSessionStatus status, Pageable pageable);

    Page<ReceiptSession> findByOrganizationIdAndWarehouseIdAndStatus(
            UUID organizationId, UUID warehouseId, ReceiptSessionStatus status, Pageable pageable);

    List<ReceiptSession> findByStatusAndCreatedAtBefore(
            @Param("status") ReceiptSessionStatus status, @Param("cutoff") LocalDateTime cutoff);
}
