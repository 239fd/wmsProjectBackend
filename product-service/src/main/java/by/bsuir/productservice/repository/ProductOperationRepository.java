package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.enums.OperationType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductOperationRepository
        extends JpaRepository<ProductOperation, UUID>, JpaSpecificationExecutor<ProductOperation> {

    List<ProductOperation> findByProductIdOrderByOperationDateDesc(UUID productId);

    List<ProductOperation> findByWarehouseIdOrderByOperationDateDesc(UUID warehouseId);

    List<ProductOperation> findByOperationType(OperationType operationType);

    List<ProductOperation> findByOperationDateBetween(LocalDateTime start, LocalDateTime end);

    List<ProductOperation> findByOrganizationIdOrderByOperationDateDesc(UUID organizationId);

    List<ProductOperation> findByOrganizationIdAndWarehouseIdOrderByOperationDateDesc(UUID organizationId, UUID warehouseId);

    List<ProductOperation> findByOrganizationIdAndProductIdOrderByOperationDateDesc(UUID organizationId, UUID productId);

    List<ProductOperation> findByOrganizationIdAndBatchIdOrderByOperationDateDesc(UUID organizationId, UUID batchId);

    java.util.Optional<ProductOperation> findByOperationIdAndOrganizationId(UUID operationId, UUID organizationId);

    List<ProductOperation> findBySessionId(UUID sessionId);

    default Page<ProductOperation> searchHistory(
            UUID orgId,
            OperationType type,
            UUID warehouseId,
            UUID userId,
            UUID productId,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable) {
        Specification<ProductOperation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (orgId != null) predicates.add(cb.equal(root.get("organizationId"), orgId));
            if (type != null) predicates.add(cb.equal(root.get("operationType"), type));
            if (warehouseId != null) predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            if (userId != null) predicates.add(cb.equal(root.get("userId"), userId));
            if (productId != null) predicates.add(cb.equal(root.get("productId"), productId));
            if (start != null) predicates.add(cb.greaterThanOrEqualTo(root.get("operationDate"), start));
            if (end != null) predicates.add(cb.lessThanOrEqualTo(root.get("operationDate"), end));
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return findAll(spec, pageable);
    }
}
