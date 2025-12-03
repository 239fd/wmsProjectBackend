package by.bsuir.organizationservice.repository;

import by.bsuir.organizationservice.model.entity.OrganizationInvitationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationInvitationCodeRepository extends JpaRepository<OrganizationInvitationCode, UUID> {

    Optional<OrganizationInvitationCode> findByInvitationCode(String invitationCode);

    List<OrganizationInvitationCode> findByOrgIdAndIsActiveTrue(UUID orgId);

    List<OrganizationInvitationCode> findByOrgIdAndWarehouseIdAndIsActiveTrue(UUID orgId, UUID warehouseId);

    @Modifying
    @Query("UPDATE OrganizationInvitationCode o SET o.isActive = false WHERE o.orgId = :orgId")
    void deactivateAllByOrgId(UUID orgId);

    @Modifying
    @Query("UPDATE OrganizationInvitationCode o SET o.isActive = false WHERE o.orgId = :orgId AND o.warehouseId = :warehouseId")
    void deactivateAllByOrgIdAndWarehouseId(UUID orgId, UUID warehouseId);

    @Modifying
    @Query("UPDATE OrganizationInvitationCode o SET o.isActive = false WHERE o.expiresAt < :now AND o.isActive = true")
    int deactivateExpiredCodes(LocalDateTime now);
}
