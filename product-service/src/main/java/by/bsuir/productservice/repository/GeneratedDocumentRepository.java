package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.GeneratedDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {

    Page<GeneratedDocument> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<GeneratedDocument> findByOrganizationIdAndDocumentType(
            UUID organizationId, String documentType, Pageable pageable);

    List<GeneratedDocument> findByOrganizationIdAndOperationIdOrderByGeneratedAtDesc(
            UUID organizationId, UUID operationId);

    Optional<GeneratedDocument> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
