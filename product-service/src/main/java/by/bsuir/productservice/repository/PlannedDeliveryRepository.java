package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.PlannedDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlannedDeliveryRepository extends JpaRepository<PlannedDelivery, UUID> {

    boolean existsByExternalId(String externalId);

    Optional<PlannedDelivery> findByExternalId(String externalId);

    List<PlannedDelivery> findByProcessedFalseOrderByExpectedDateAsc();

    Page<PlannedDelivery> findByProcessedFalseOrderByExpectedDateAsc(Pageable pageable);
}