package by.bsuir.organizationservice.repository;

import by.bsuir.organizationservice.model.entity.OrganizationReadModel;
import by.bsuir.organizationservice.model.enums.OrganizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationReadModelRepository extends JpaRepository<OrganizationReadModel, UUID> {

    Optional<OrganizationReadModel> findByOrgId(UUID orgId);

    Optional<OrganizationReadModel> findByUnp(String unp);

    Optional<OrganizationReadModel> findByName(String name);

    boolean existsByUnp(String unp);

    boolean existsByName(String name);

    List<OrganizationReadModel> findAllByStatus(OrganizationStatus status);
}
