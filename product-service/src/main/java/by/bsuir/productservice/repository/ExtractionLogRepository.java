package by.bsuir.productservice.repository;

import by.bsuir.productservice.model.entity.ExtractionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExtractionLogRepository extends JpaRepository<ExtractionLog, Long> {

    List<ExtractionLog> findTop10BySourceOrderByExtractedAtDesc(String source);
}