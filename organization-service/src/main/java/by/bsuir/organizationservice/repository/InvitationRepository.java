package by.bsuir.organizationservice.repository;

import by.bsuir.organizationservice.model.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByInvitationToken(UUID invitationToken);

    Optional<Invitation> findByInvitationTokenAndUsedFalse(UUID invitationToken);

    List<Invitation> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    List<Invitation> findByEmailAndUsedFalse(String email);

    @Modifying
    @Query("DELETE FROM Invitation i WHERE i.expiresAt < :now AND i.used = false")
    void deleteExpiredInvitations(LocalDateTime now);
}