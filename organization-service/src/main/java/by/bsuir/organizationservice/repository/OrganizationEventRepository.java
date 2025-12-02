package by.bsuir.organizationservice.repository;

import by.bsuir.organizationservice.model.entity.OrganizationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrganizationEventRepository extends JpaRepository<OrganizationEvent, Long> {

    List<OrganizationEvent> findByOrgIdOrderByCreatedAtAsc(UUID orgId);
}
