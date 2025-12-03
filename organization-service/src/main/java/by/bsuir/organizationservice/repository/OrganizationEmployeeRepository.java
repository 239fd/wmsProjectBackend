package by.bsuir.organizationservice.repository;

import by.bsuir.organizationservice.model.entity.OrganizationEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationEmployeeRepository extends JpaRepository<OrganizationEmployee, UUID> {

    List<OrganizationEmployee> findByOrgIdAndIsActiveTrue(UUID orgId);

    Optional<OrganizationEmployee> findByUserIdAndOrgIdAndIsActiveTrue(UUID userId, UUID orgId);

    boolean existsByUserIdAndOrgIdAndIsActiveTrue(UUID userId, UUID orgId);
}

