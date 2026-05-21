package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    List<Supplier> findByIsActiveTrue();

    Page<Supplier> findByIsActiveTrue(Pageable pageable);

    boolean existsByUnpAndIsActiveTrue(String unp);

    List<Supplier> findByOrganizationId(UUID organizationId);

    List<Supplier> findByOrganizationIdAndIsActiveTrue(UUID organizationId);

    Page<Supplier> findByOrganizationIdAndIsActiveTrue(UUID organizationId, Pageable pageable);

    boolean existsByOrganizationIdAndUnpAndIsActiveTrue(UUID organizationId, String unp);

    java.util.Optional<Supplier> findFirstByOrganizationIdAndUnp(UUID organizationId, String unp);

    java.util.Optional<Supplier> findFirstByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);
}