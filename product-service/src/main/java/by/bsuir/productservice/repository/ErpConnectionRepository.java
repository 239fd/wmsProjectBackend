package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ErpConnection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ErpConnectionRepository extends JpaRepository<ErpConnection, UUID> {

    List<ErpConnection> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<ErpConnection> findByConnectionIdAndOrganizationId(UUID connectionId, UUID organizationId);

    Optional<ErpConnection> findFirstByOrganizationIdAndAggregatorAndIsDefaultTrue(
            UUID organizationId, String aggregator);

    Optional<ErpConnection> findFirstByOrganizationIdAndIsDefaultTrue(UUID organizationId);

    List<ErpConnection> findByOrganizationIdAndIsDefaultTrue(UUID organizationId);
}
