package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.DocumentCounter;
import by.bsuir.productservice.model.entity.DocumentCounterId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentCounterRepository
        extends JpaRepository<DocumentCounter, DocumentCounterId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DocumentCounter c "
            + "WHERE c.organizationId = :orgId "
            + "AND c.documentType = :type "
            + "AND c.year = :year")
    Optional<DocumentCounter> findForUpdate(
            @Param("orgId") UUID orgId,
            @Param("type") String type,
            @Param("year") Integer year);
}
