package by.bsuir.organizationservice.repository;

import by.bsuir.organizationservice.model.entity.OrganizationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrganizationEventRepository extends JpaRepository<OrganizationEvent, Long> {

    List<OrganizationEvent> findByOrgIdOrderByCreatedAtAsc(UUID orgId);

    @Query("SELECT MAX(e.eventVersion) FROM OrganizationEvent e WHERE e.orgId = :orgId")
    Integer findMaxVersionByOrgId(@Param("orgId") UUID orgId);

    @Modifying
    @Query("DELETE FROM OrganizationEvent e WHERE e.orgId = :orgId")
    void deleteByOrgId(@Param("orgId") UUID orgId);
}
